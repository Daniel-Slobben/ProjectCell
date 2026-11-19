package slobben.cells.service.workers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import slobben.cells.dto.incoming.ClientUpdateRequest;
import slobben.cells.dto.outgoing.BlockFrame;
import slobben.cells.dto.outgoing.EncodedBlock;
import slobben.cells.dto.outgoing.StatusResponse;
import slobben.cells.entities.Block;
import slobben.cells.entities.Client;
import slobben.cells.service.ExecutorService;
import slobben.cells.util.BlockUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static slobben.cells.dto.outgoing.StatusResponse.Type.NO_BLOCKS;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClientService extends Worker {
    private static final String DESTINATION = "/queue/blocks-user%s";
    // clients that watch sectors get the sectors of the blocks in view this often, all on the same tick
    private static final int SECTORS_MS = 10000;
    // ~120 bytes a block: a message of ~30 KB, like a full block, so a slow connection sees the sectors come in
    private static final int SECTOR_BLOCKS_PER_MESSAGE = 250;
    // the frontend watches sectors long before this many blocks are in view; more full blocks would only be a burden
    private static final int MAX_CELL_BLOCKS = 2000;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final ExecutorService executorService;
    private final Map<String, Block> blocks;
    private final Map<String, Client> clients = new ConcurrentHashMap<>();
    @Value("${cells.targetspeed}")
    private int targetSpeed;
    private int ticks = 0;

    @Override
    public String getName() {
        return "Client updates with %s amount of clients".formatted(clients.size());
    }

    public void execute() {
        int tick = ticks++;
        boolean sectorTick = tick % ticksPer(SECTORS_MS) == 0;
        Set<Runnable> tasks = clients.values().stream()
                .filter(client -> sectorTick || !client.isSectors())
                .map(client -> (Runnable) () -> {
                    if (client.isSectors()) sendSectors(client, client.getView(), null);
                    else sendClientUpdate(client, client.getActiveBlocks(), true);
                })
                .collect(Collectors.toSet());

        executorService.executeTasksParallel(tasks, getName());
    }

    private int ticksPer(int ms) {
        return Math.max(1, ms / Math.max(1, targetSpeed));
    }

    // the sectors of the blocks in view, but not in the old view if there is one
    private void sendSectors(Client client, ClientUpdateRequest view, ClientUpdateRequest oldView) {
        List<EncodedBlock> sectors = blocks.values().stream()
                .filter(block -> inView(view, block) && (oldView == null || !inView(oldView, block)))
                .map(Block::getEncodedBlockSectors).filter(Objects::nonNull).toList();
        for (int i = 0; i < sectors.size(); i += SECTOR_BLOCKS_PER_MESSAGE) {
            send(client, BlockFrame.write(sectors.subList(i, Math.min(i + SECTOR_BLOCKS_PER_MESSAGE, sectors.size()))));
        }
    }

    private static boolean inView(ClientUpdateRequest view, Block block) {
        return block.getX() >= view.minX() && block.getX() <= view.maxX() && block.getY() >= view.minY() && block.getY() <= view.maxY();
    }

    @EventListener
    public void onConnect(SessionConnectEvent event) {
        String sessionId = SimpMessageHeaderAccessor.getSessionId(event.getMessage().getHeaders());
        clients.put(sessionId, new Client(sessionId));
    }

    // Also comes when the broker closes a session that stopped sending heartbeats.
    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        clients.remove(event.getSessionId());
    }

    public void sendClientUpdate(Client client, Set<String> blockKeys, boolean sendBorderBlocks) {
        List<Block> watched = blockKeys.stream().map(blocks::get).filter(Objects::nonNull).toList();
        if (sendBorderBlocks) {
            send(client, BlockFrame.write(watched.stream().map(Block::getEncodedBlockBorders).filter(Objects::nonNull).toList()));
        } else {
            // Full blocks are ~30 KB each, one message per block: on a slow connection the client sees them come in,
            // instead of nothing until a message of megabytes has arrived and its watchdog gives up. Each block is
            // encoded in its own thread, so the first one goes out after one encoding instead of after all of them.
            try (var virtualExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (Block block : watched) {
                    virtualExecutor.execute(() -> {
                        EncodedBlock encoded = block.getEncodedBlock();
                        if (encoded != null) send(client, BlockFrame.write(List.of(encoded)));
                    });
                }
            }
        }
    }

    private void send(Client client, Object payload) {
        simpMessagingTemplate.convertAndSend(DESTINATION.formatted(client.getSessionId()), payload);
    }

    public void updateClientBlocks(String sessionId, ClientUpdateRequest view) {
        Client client = clients.get(sessionId);
        if (client == null) {
            log.debug("Update for a closed session {}", sessionId);
            return;
        }
        if (!view.watchesSectors() && view.blockCount() > MAX_CELL_BLOCKS) {
            log.debug("Session {} asks for {} blocks as cells", sessionId, view.blockCount());
            return;
        }
        ClientUpdateRequest oldView = client.getView();
        boolean switched = client.isSectors() != view.watchesSectors();
        client.setView(view);
        client.setSectors(view.watchesSectors());

        // A client that comes back to a part of the world that was pruned in the meantime finds nothing there.
        if (oldView == null && blocks.values().stream().noneMatch(block -> inView(view, block))) {
            send(client, new StatusResponse(NO_BLOCKS));
            return;
        }
        if (client.isSectors()) {
            client.getActiveBlocks().clear();
            sendSectors(client, view, switched ? null : oldView);
            return;
        }
        Set<String> inView = new HashSet<>();
        for (int x = view.minX(); x <= view.maxX(); x++) {
            for (int y = view.minY(); y <= view.maxY(); y++) inView.add(BlockUtils.getKey(x, y));
        }
        Set<String> clientBlocks = client.getActiveBlocks();
        Set<String> added = new HashSet<>(inView);
        added.removeAll(clientBlocks);
        Arrays.stream(view.resendKeys()).filter(inView::contains).forEach(added::add);
        clientBlocks.retainAll(inView);
        clientBlocks.addAll(inView);
        sendClientUpdate(client, added, false);
    }
}
