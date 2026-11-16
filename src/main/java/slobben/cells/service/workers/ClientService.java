package slobben.cells.service.workers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    // clients that watch sectors get them every this many ticks, all on the same tick
    private static final int SECTOR_TICKS = 4;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final ExecutorService executorService;
    private final Map<String, Block> blocks;
    private final Map<String, Client> clients = new ConcurrentHashMap<>();
    private int ticks = 0;

    @Override
    public String getName() {
        return "Client updates with %s amount of clients".formatted(clients.size());
    }

    public void execute() {
        boolean sectorTick = ticks++ % SECTOR_TICKS == 0;
        Set<Runnable> tasks = clients.values().stream()
                .filter(client -> sectorTick || !client.isSectors())
                .map(client -> (Runnable) () -> sendClientUpdate(client, client.getActiveBlocks(), !client.isInError()))
                .collect(Collectors.toSet());

        executorService.executeTasksParallel(tasks, getName());
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
        if (client.isSectors()) {
            send(client, BlockFrame.write(watched.stream().map(Block::getEncodedBlockSectors).filter(Objects::nonNull).toList()));
        } else if (sendBorderBlocks) {
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
        boolean firstUpdate = clientBlocks.isEmpty();
        boolean switched = client.isSectors() != clientUpdateRequest.watchesSectors();
        client.setSectors(clientUpdateRequest.watchesSectors());

        Arrays.asList(clientUpdateRequest.blocksToRemove()).forEach(clientBlocks::remove);
        clientBlocks.addAll(Arrays.asList(clientUpdateRequest.blocksToAdd()));

        // A client that comes back to a part of the world that was pruned in the meantime finds nothing there.
        if (firstUpdate && Arrays.stream(clientUpdateRequest.blocksToAdd()).noneMatch(blocks::containsKey)) {
            send(client, new StatusResponse(NO_BLOCKS));
            return;
        }
        // switching between cells and sectors, the client needs all its blocks again
        sendClientUpdate(client, switched ? clientBlocks : Set.of(clientUpdateRequest.blocksToAdd()), false);
    }
}
