package slobben.cells.service.workers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import slobben.cells.config.EnvironmentConfig;
import slobben.cells.dto.incoming.ClientUpdateRequest;
import slobben.cells.dto.outgoing.EncodedBlock;
import slobben.cells.dto.outgoing.HealthCheckResponse;
import slobben.cells.entities.model.Block;
import slobben.cells.entities.model.Client;
import slobben.cells.enums.Direction;
import slobben.cells.errors.NotAClientException;
import slobben.cells.service.ExecutorService;
import slobben.cells.util.BlockCoordinatesResult;
import slobben.cells.util.BlockUtils;

import java.util.*;
import java.util.stream.Collectors;

import static slobben.cells.dto.outgoing.HealthCheckResponse.HEALTH_CHECK_TYPE.HEALTH_ACK;
import static slobben.cells.dto.outgoing.HealthCheckResponse.HEALTH_CHECK_TYPE.SESSION_DEAD;
import static slobben.cells.util.Utils.getBlockCoordinates;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClientService implements Worker {
    private static final int HEALTH_CHECK_LIMIT_MS = 20_000;
    private static final String TOPIC = "/topic/%s";
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final ExecutorService executorService;
    private final EnvironmentConfig environmentConfig;
    private final Map<String, Block> blocks;
    private final List<Client> clients = new ArrayList<>();

    @Override
    public String getName() {
        return "Client updates with %s amount of clients".formatted(clients.size());
    }

    public void execute() {
        Set<Runnable> tasks = clients.stream().map(client -> (Runnable) () -> sendClientUpdate(client, client.getActiveBlocks(), !client.isInError())).collect(Collectors.toSet());

        executorService.executeTasksParallel(tasks, getName());
    }

    public void sendClientUpdate(Client client, List<String> blockKeys, boolean sendBorderBlocks) {
        // Client health check
        if (sendBorderBlocks && client.getHealthCheck() + HEALTH_CHECK_LIMIT_MS < System.currentTimeMillis()) {
            disconnectClient(client);
            return;
        }
        client.resetHealthCheck();

        List<EncodedBlock> copyOfBlocks = getEncodedBlocks(blockKeys, sendBorderBlocks);
        simpMessagingTemplate.convertAndSend(TOPIC.formatted(client.getClientId()), copyOfBlocks);
    }

    private @NonNull List<EncodedBlock> getEncodedBlocks(List<String> blockKeys, boolean sendBorderBlocks) {
        return blockKeys.stream().map(blocks::get).filter(Objects::nonNull).map(block -> {
            if (sendBorderBlocks) {
                return block.getEncodedBlockBorders();
            } else {
                return block.getEncodedBlock();
            }
        }).toList();
    }

    public void disconnectClient(Client client) {
        this.clients.remove(client);
        simpMessagingTemplate.convertAndSend(TOPIC.formatted(client.getClientId()), new HealthCheckResponse(SESSION_DEAD));
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
        Client client = new Client();
        clients.add(client);
        return client.getClientId();
    }

    public void healthCheck(UUID clientId) {
        log.debug("Received healthcheck for client {}", clientId);

        findClient(clientId).resetHealthCheck();
        simpMessagingTemplate.convertAndSend(TOPIC.formatted(clientId), new HealthCheckResponse(HEALTH_ACK));
    }

    public void addErrorClient(UUID clientId) {
        findClient(clientId).setInError(true);
    }

    public Client updateClientBlocks(ClientUpdateRequest clientUpdateRequest) {
        Client client = findClient(clientUpdateRequest.client());
        List<String> clientBlocks = client.getActiveBlocks();

        clientBlocks.removeAll(Arrays.asList(clientUpdateRequest.blocksToRemove()));
        clientBlocks.addAll(Arrays.asList(clientUpdateRequest.blocksToAdd()));

        return client;
    }

    public List<EncodedBlock> getInitialBlocks(int worldX, int worldY) {
        BlockCoordinatesResult result = getBlockCoordinates(worldX, worldY, environmentConfig.getBlockSize());
        String centerBlock = BlockUtils.getKey(result.blockX(), result.blockY());

        List<String> blocksToAdd = new ArrayList<>(9);
        blocksToAdd.add(centerBlock);
        for (Direction direction : Direction.values()) {
            blocksToAdd.add(BlockUtils.getKey(result.blockX() + direction.getDx(), result.blockY() + direction.getDy()));
        }
        return getEncodedBlocks(blocksToAdd, false);
    }

    private Client findClient(UUID clientId) {
        Optional<Client> optionalClient = clients.stream().filter(client -> client.getClientId().equals(clientId)).findFirst();

        if (optionalClient.isEmpty()) {
            simpMessagingTemplate.convertAndSend(TOPIC.formatted(clientId), new HealthCheckResponse(SESSION_DEAD));
            throw new NotAClientException("Client not found. Send SESSION_DEAD response. ID:" + clientId.toString());
        }

        return optionalClient.get();
    }
}
