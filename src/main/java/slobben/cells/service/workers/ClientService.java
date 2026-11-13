package slobben.cells.service.workers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
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
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final ExecutorService executorService;
    private final Map<String, Block> blocks;
    private final Map<String, Client> clients = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "Client updates with %s amount of clients".formatted(clients.size());
    }

    public void execute() {
        Set<Runnable> tasks = clients.values().stream().map(client -> (Runnable) () -> sendClientUpdate(client, client.getActiveBlocks(), !client.isInError())).collect(Collectors.toSet());

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
        List<EncodedBlock> copyOfBlocks = getEncodedBlocks(blockKeys, sendBorderBlocks).stream().filter(Objects::nonNull).toList();
        if (sendBorderBlocks) {
            send(client, BlockFrame.write(copyOfBlocks));
        } else {
            // Full blocks are ~75 KB each, one message per block: on a slow connection the client sees them come in,
            // instead of nothing until a message of megabytes has arrived and its watchdog gives up.
            try (var virtualExector = Executors.newVirtualThreadPerTaskExecutor()) {
                for (EncodedBlock block : copyOfBlocks) {
                    virtualExector.execute(() -> send(client, BlockFrame.write(List.of(block))));
                }
            }
        }
    }

    private void send(Client client, Object payload) {
        simpMessagingTemplate.convertAndSend(DESTINATION.formatted(client.getSessionId()), payload);
    }

    private @NonNull List<EncodedBlock> getEncodedBlocks(Set<String> blockKeys, boolean sendBorderBlocks) {
        return blockKeys.stream().map(blocks::get).filter(Objects::nonNull).map(block -> {
            if (sendBorderBlocks) {
                return block.getEncodedBlockBorders();
            } else {
                return block.getEncodedBlock();
            }
        }).toList();
    }

    public void updateClientBlocks(String sessionId, ClientUpdateRequest clientUpdateRequest) {
        Client client = clients.get(sessionId);
        if (client == null) {
            log.debug("Update for a closed session {}", sessionId);
            return;
        }
        Set<String> clientBlocks = client.getActiveBlocks();
        boolean firstUpdate = clientBlocks.isEmpty();

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
