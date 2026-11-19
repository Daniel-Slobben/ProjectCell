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
    // Clients that watch sectors ask for no blocks: they get the sectors of every block this often.
    private static final int SECTOR_MAP_MS = 10000;
    // ~120 bytes a block: a message of ~30 KB, like a full block, so a slow connection sees the map come in
    private static final int SECTOR_MAP_BLOCKS_PER_MESSAGE = 250;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final ExecutorService executorService;
    private final Map<String, Block> blocks;
    private final Map<String, Client> clients = new ConcurrentHashMap<>();
    @Value("${cells.targetspeed}")
    private int targetSpeed;
    private int ticks = 0;
    private volatile List<byte[]> sectorMap = List.of();

    @Override
    public String getName() {
        return "Client updates with %s amount of clients".formatted(clients.size());
    }

    public void execute() {
        int tick = ticks++;
        boolean sectorMapTick = tick % ticksPer(SECTOR_MAP_MS) == 0;
        if (sectorMapTick) sectorMap = writeSectorMap();
        Set<Runnable> tasks = clients.values().stream()
                .filter(client -> sectorMapTick || !client.isSectors())
                .map(client -> (Runnable) () -> {
                    if (client.isSectors()) sendSectorMap(client);
                    else sendClientUpdate(client, client.getActiveBlocks(), true);
                })
                .collect(Collectors.toSet());

        executorService.executeTasksParallel(tasks, getName());
    }

    private int ticksPer(int ms) {
        return Math.max(1, ms / Math.max(1, targetSpeed));
    }

    private List<byte[]> writeSectorMap() {
        List<EncodedBlock> sectors = blocks.values().stream().map(Block::getEncodedBlockSectors).filter(Objects::nonNull).toList();
        List<byte[]> messages = new ArrayList<>();
        for (int i = 0; i < sectors.size(); i += SECTOR_MAP_BLOCKS_PER_MESSAGE) {
            messages.add(BlockFrame.write(sectors.subList(i, Math.min(i + SECTOR_MAP_BLOCKS_PER_MESSAGE, sectors.size()))));
        }
        return messages;
    }

    private void sendSectorMap(Client client) {
        for (byte[] message : sectorMap) send(client, message);
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

    public void updateClientBlocks(String sessionId, ClientUpdateRequest clientUpdateRequest) {
        Client client = clients.get(sessionId);
        if (client == null) {
            log.debug("Update for a closed session {}", sessionId);
            return;
        }
        Set<String> clientBlocks = client.getActiveBlocks();
        boolean switched = client.isSectors() != clientUpdateRequest.watchesSectors();
        boolean firstUpdate = clientBlocks.isEmpty() && !switched;
        client.setSectors(clientUpdateRequest.watchesSectors());

        if (client.isSectors()) {
            clientBlocks.clear();
            if (switched) sendSectorMap(client);
            return;
        }
        Arrays.asList(clientUpdateRequest.blocksToRemove()).forEach(clientBlocks::remove);
        clientBlocks.addAll(Arrays.asList(clientUpdateRequest.blocksToAdd()));

        // A client that comes back to a part of the world that was pruned in the meantime finds nothing there.
        if (firstUpdate && Arrays.stream(clientUpdateRequest.blocksToAdd()).noneMatch(blocks::containsKey)) {
            send(client, new StatusResponse(NO_BLOCKS));
            return;
        }
        sendClientUpdate(client, Set.of(clientUpdateRequest.blocksToAdd()), false);
    }
}
