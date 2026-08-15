package slobben.cells.service.workers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import slobben.cells.config.EnvironmentConfig;
import slobben.cells.dto.ClientUpdateRequest;
import slobben.cells.entities.model.Block;
import slobben.cells.errors.NotAClientException;
import slobben.cells.service.ExecutorService;
import slobben.cells.util.BlockUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClientService implements Worker {
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final EnvironmentConfig environmentConfig;
    private final ExecutorService executorService;

    private final Map<UUID, ConcurrentLinkedQueue<Block>> activeClients = new ConcurrentHashMap<>();
    private final List<UUID> errorClients = new ArrayList<>();
    private final Map<String, Block> blocks;
    private final Map<String, Block> ghostBlocks;

    @Override
    public String getName() {
        return "Client updates";
    }

    public void execute() {
        Set<Runnable> tasks = activeClients.entrySet().stream()
                .filter(entry -> !errorClients.contains(entry.getKey()))
                .map(entrySet ->
                        (Runnable) () -> updateClientWithBorder(entrySet.getKey(), entrySet.getValue()))
                .collect(Collectors.toSet());

        tasks.addAll(errorClients.stream()
                .map(uuid -> (Runnable) () -> updateClientWithFullBlocks(uuid, activeClients.get(uuid)))
                .collect(Collectors.toSet()));
        executorService.executeTasksParallel(tasks);

        errorClients.clear();
    }

    public void updateClientWithBorder(UUID uuid, Queue<Block> blocks) {
        var copyOfBlocks = List.copyOf(blocks).stream()
                .map(Block::getEncodedBlockBorders).toList();

        simpMessagingTemplate.convertAndSend("/topic/%s".formatted(uuid), copyOfBlocks);
    }

    public void updateClientWithFullBlocks(UUID uuid, Queue<Block> blocks) {
        var copyOfBlocks = List.copyOf(blocks).stream()
                .map(Block::getEncodedBlock).toList();

        simpMessagingTemplate.convertAndSend("/topic/full/%s".formatted(uuid), copyOfBlocks);
    }

    public void disconnectClient(UUID uuid) {
        activeClients.remove(uuid);
    }

    public void addClient(UUID uuid) {
        activeClients.put(uuid, new ConcurrentLinkedQueue<>());
    }

    public void addErrorClient(UUID uuid) {
        this.errorClients.add(uuid);
    }

    public void updateClientWithId(UUID uuid, String[] blocksToGet) {
        var copyOfBlocks = getBlocksFromKeys(Arrays.stream(blocksToGet).collect(Collectors.toSet())).stream()
                .map(Block::getEncodedBlock).toList();

        simpMessagingTemplate.convertAndSend("/topic/full/%s".formatted(uuid), copyOfBlocks);
    }

    private Block getNewGhostBlock(String key) {
        var blockSizeWithBorder = environmentConfig.getBlockSizeWithBorder();
        var keyPair = BlockUtils.resolveKey(key);
        Block newBlock = Block.builder().x(keyPair.getFirst()).y(keyPair.getSecond()).cells(new boolean[blockSizeWithBorder][blockSizeWithBorder]).ghostBlock(true).build();
        ghostBlocks.put(key, newBlock);
        return newBlock;
    }

    public void updateClientBlocks(ClientUpdateRequest clientUpdateRequest) {
        if (activeClients.containsKey(clientUpdateRequest.client())) {
            var clientBlocks = activeClients.get(clientUpdateRequest.client());

            clientBlocks.removeIf(block -> Set.of(clientUpdateRequest.blocksToRemove()).contains(BlockUtils.getKey(block.getX(), block.getY())));
            clientBlocks.addAll(getBlocksFromKeys(Set.of(clientUpdateRequest.blocksToAdd())));
        } else {
            throw new NotAClientException("Client not found: %s".formatted(clientUpdateRequest.client()));
        }
    }

    public Set<Block> getBlocksFromKeys(Set<String> keys) {
        Set<Block> blocksToAdd = new HashSet<>();
        for (String key : keys) {
            Block block = blocks.get(key);
            if (block == null) {
                block = getNewGhostBlock(key);
            }
            blocksToAdd.add(block);
        }
        return blocksToAdd;
    }
}
