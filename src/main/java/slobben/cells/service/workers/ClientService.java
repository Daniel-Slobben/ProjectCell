package slobben.cells.service.workers;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import slobben.cells.config.EnvironmentConfig;
import slobben.cells.dto.incoming.ClientUpdateRequest;
import slobben.cells.dto.outgoing.EncodedBlock;
import slobben.cells.entities.model.Block;
import slobben.cells.enums.CornerEnum;
import slobben.cells.errors.NotAClientException;
import slobben.cells.service.ExecutorService;
import slobben.cells.util.BlockCoordinatesResult;
import slobben.cells.util.BlockUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static slobben.cells.enums.CornerEnum.*;
import static slobben.cells.util.Utils.getBlockCoordinates;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClientService implements Worker {
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final ExecutorService executorService;
    private final EnvironmentConfig environmentConfig;
    private int blockSize;

    private static final int HEALTH_CHECK_LIMIT = 20;

    private final Map<UUID, List<String>> activeClients = new ConcurrentHashMap<>();
    private final Map<String, Block> blocks;
    private final Map<UUID, Integer> healthcheckClients = new HashMap<>();
    private final Set<UUID> errorClients = new HashSet<>();

    @Override
    public String getName() {
        return "Client updates with %s amount of clients".formatted(activeClients.size());
    }

    @PostConstruct
    void init() {
        this.blockSize = environmentConfig.getBlockSize();
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
        // Client health check
        if (sendBorderBlocks) {
            Integer health = healthcheckClients.get(uuid);
            if (health > HEALTH_CHECK_LIMIT) {
                // deleting client after 20 ticks
                disconnectClient(uuid);
                return;
            }
            healthcheckClients.put(uuid, health + 1);
        }

        List<EncodedBlock> copyOfBlocks = getEncodedBlocks(blockKeys, sendBorderBlocks);

        simpMessagingTemplate.convertAndSend("/topic/%s".formatted(uuid), copyOfBlocks);
    }

    private @NonNull List<EncodedBlock> getEncodedBlocks(List<String> blockKeys, boolean sendBorderBlocks) {
        return blockKeys.stream()
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
    }

    public void disconnectClient(UUID uuid) {
        activeClients.remove(uuid);
        errorClients.remove(uuid);
        healthcheckClients.remove(uuid);
    }

    public boolean hasVisibleBlocks(String[] visibleBlocks) {
        for (String key : visibleBlocks) {
            if (blocks.containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    public UUID createNewClient() {
        UUID uuid = UUID.randomUUID();
        activeClients.put(uuid, new ArrayList<>());
        healthcheckClients.put(uuid, 0);
        return uuid;
    }

    public void healthCheck(UUID clientId) {
        log.debug("Received healthcheck for client {}", clientId);

        record HealthCheckResponse(String type) {
        }

        if (activeClients.containsKey(clientId)) {
            this.healthcheckClients.put(clientId, 0);
            simpMessagingTemplate.convertAndSend("/topic/%s".formatted(clientId), new HealthCheckResponse("HEALTH_ACK"));
            return;
        }
        simpMessagingTemplate.convertAndSend("/topic/%s".formatted(clientId), new HealthCheckResponse("SESSION_DEAD"));
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

    public List<EncodedBlock> getInitialBlocks(int worldX, int worldY) {
        BlockCoordinatesResult result = getBlockCoordinates(worldX, worldY, blockSize);
        CornerEnum corner;
        if (result.relativeCellX() > blockSize / 2 && result.relativeCellY() > blockSize / 2) corner = BOTTOM_RIGHT;
        else if (result.relativeCellX() <= blockSize / 2 && result.relativeCellY() > blockSize / 2) corner = TOP_RIGHT;
        else if (result.relativeCellX() > blockSize / 2 && result.relativeCellY() <= blockSize / 2)
            corner = BOTTOM_LEFT;
        else if (result.relativeCellX() <= blockSize / 2 && result.relativeCellY() <= blockSize / 2) corner = TOP_LEFT;
        else {
            throw new IllegalStateException();
        }

        String centerBlock = BlockUtils.getKey(result.blockX(), result.blockY());
        List<String> blocksToAdd = new ArrayList<>();
        blocksToAdd.add(centerBlock);
        switch (corner) {
            case TOP_LEFT -> {
                blocksToAdd.add(BlockUtils.getKey(result.blockX() - 1, result.blockY()));
                blocksToAdd.add(BlockUtils.getKey(result.blockX() - 1, result.blockY() - 1));
                blocksToAdd.add(BlockUtils.getKey(result.blockX(), result.blockY() - 1));
            }
            case TOP_RIGHT -> {
                blocksToAdd.add(BlockUtils.getKey(result.blockX() + 1, result.blockY()));
                blocksToAdd.add(BlockUtils.getKey(result.blockX() + 1, result.blockY() - 1));
                blocksToAdd.add(BlockUtils.getKey(result.blockX(), result.blockY() - 1));
            }
            case BOTTOM_LEFT -> {
                blocksToAdd.add(BlockUtils.getKey(result.blockX() - 1, result.blockY()));
                blocksToAdd.add(BlockUtils.getKey(result.blockX() - 1, result.blockY() + 1));
                blocksToAdd.add(BlockUtils.getKey(result.blockX(), result.blockY() + 1));
            }
            case BOTTOM_RIGHT -> {
                blocksToAdd.add(BlockUtils.getKey(result.blockX() + 1, result.blockY()));
                blocksToAdd.add(BlockUtils.getKey(result.blockX() + 1, result.blockY() + 1));
                blocksToAdd.add(BlockUtils.getKey(result.blockX(), result.blockY() + 1));
            }
        }
        return getEncodedBlocks(blocksToAdd, false);
    }
}
