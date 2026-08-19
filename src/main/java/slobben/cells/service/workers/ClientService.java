package slobben.cells.service.workers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import slobben.cells.dto.incoming.ClientUpdateRequest;
import slobben.cells.dto.outgoing.EncodedBlock;
import slobben.cells.entities.model.Block;
import slobben.cells.errors.NotAClientException;
import slobben.cells.service.ExecutorService;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClientService implements Worker {
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final ExecutorService executorService;

    private final Map<UUID, List<String>> activeClients = new ConcurrentHashMap<>();
    private final List<UUID> errorClients = new ArrayList<>();
    private final Map<String, Block> blocks;

    @Override
    public String getName() {
        return "Client updates";
    }

    public void execute() {
        Set<Runnable> tasks = activeClients.entrySet().stream()
                .filter(entry -> !errorClients.contains(entry.getKey()))
                .map(entrySet ->
                        (Runnable) () -> sendClientUpdate(entrySet.getKey(), entrySet.getValue(), true))
                .collect(Collectors.toSet());

        tasks.addAll(errorClients.stream()
                .map(uuid -> (Runnable) () -> sendClientUpdate(uuid, activeClients.get(uuid), false))
                .collect(Collectors.toSet()));

        executorService.executeTasksParallel(tasks);

        errorClients.clear();
    }

    public void sendClientUpdate(UUID uuid, List<String> blockKeys, boolean sendBorderBlocks) {
        List<EncodedBlock> copyOfBlocks = blockKeys.stream()
                .map(blocks::get)
                .filter(Objects::nonNull)
                .map(block -> {
                    if (sendBorderBlocks) {
                        return block.getEncodedBlockBorders();
                    } else {
                        return block.getEncodedBlock();
                    }
                })
                .toList();

        simpMessagingTemplate.convertAndSend("/topic/%s".formatted(uuid), copyOfBlocks);
    }

    public void disconnectClient(UUID uuid) {
        activeClients.remove(uuid);
    }

    public void addClient(UUID uuid) {
        activeClients.put(uuid, new ArrayList<>());
    }

    public void addErrorClient(UUID uuid) {
        this.errorClients.add(uuid);
    }

    public void updateClientBlocks(ClientUpdateRequest clientUpdateRequest) {
        if (activeClients.containsKey(clientUpdateRequest.client())) {
            List<String> clientBlocks = activeClients.get(clientUpdateRequest.client());

            clientBlocks.removeAll(Arrays.asList(clientUpdateRequest.blocksToRemove()));
            clientBlocks.addAll(Arrays.asList(clientUpdateRequest.blocksToAdd()));
        } else {
            throw new NotAClientException("Client not found: %s".formatted(clientUpdateRequest.client()));
        }
    }
}
