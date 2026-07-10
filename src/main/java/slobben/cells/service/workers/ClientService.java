package slobben.cells.service.workers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.util.Pair;
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
    private final Map<String, Block> blocks;
    private final Map<String, Block> ghostBlocks;

    @Override
    public String getName() {
        return "Client updates";
    }

    public void execute() {
        Set<Runnable> tasks = activeClients.entrySet().stream().map(entrySet -> (Runnable) () -> updateClient(entrySet.getKey(), entrySet.getValue())).collect(Collectors.toSet());
        executorService.executeTasksParallel(tasks);
    }

    public void disconnectClient(UUID uuid) {
        activeClients.remove(uuid);
    }

    public void addClient(UUID uuid) {
        activeClients.put(uuid, new ConcurrentLinkedQueue<>());
    }

    public void updateClientWithId(UUID uuid) {
        this.updateClient(uuid, activeClients.get(uuid));
    }

    public void updateClient(UUID uuid, Queue<Block> blocks) {
        var copyOfBlocks = List.copyOf(blocks).stream()
                .map(Block::getEncodedBlock).toList();

        simpMessagingTemplate.convertAndSend("/topic/%s".formatted(uuid), copyOfBlocks);
    }

    private Block getNewGhostBlock(Pair<Integer, Integer> coordinates) {
        var blockSizeWithBorder = environmentConfig.getBlockSizeWithBorder();
        Block newBlock = Block.builder().x(coordinates.getFirst()).y(coordinates.getSecond()).cells(new boolean[blockSizeWithBorder][blockSizeWithBorder]).ghostBlock(true).build();
        ghostBlocks.put(BlockUtils.getKey(newBlock.getX(), newBlock.getY()), newBlock);
        return newBlock;
    }

    public Block getBlock(int x, int y) {
        var optionalBlock = blocks.values().stream().filter(block -> block.getX() == x && block.getY() == y).findFirst();
        return optionalBlock.orElseGet(() -> getNewGhostBlock(Pair.of(x, y)));
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
            var coordinates = BlockUtils.resolveKey(key);
            blocksToAdd.add(getBlock(coordinates.getFirst(), coordinates.getSecond()));
        }
        return blocksToAdd;
    }
}
