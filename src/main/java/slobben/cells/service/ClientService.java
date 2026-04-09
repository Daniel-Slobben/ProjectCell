package slobben.cells.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.util.Pair;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import slobben.cells.controller.ClientUpdateRequest;
import slobben.cells.controller.EncodedBlock;
import slobben.cells.entities.model.Block;
import slobben.cells.errors.NotAClientException;
import slobben.cells.util.BlockUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClientService {
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final EnvironmentService environmentService;

    private final Map<UUID, ConcurrentLinkedQueue<Block>> activeClients = new ConcurrentHashMap<>();
    private final Set<Block> blocks;
    private final Map<String, Block> ghostBlocks;

    public void tic() {
        long timer = System.currentTimeMillis();

        activeClients.entrySet().stream().parallel().forEach(entry -> updateClient(entry.getKey(), entry.getValue()));

        log.info("updating {} clients took {}ms", activeClients.size(), System.currentTimeMillis() - timer);
    }

    public void disconnectClient(UUID uuid) {
        activeClients.remove(uuid);
    }

    public void addClient(UUID uuid) {
        activeClients.put(uuid, new ConcurrentLinkedQueue<>());
    }

    public void updateClient(UUID uuid, Queue<Block> blocks) {
        var copyOfBlocks = List.copyOf(blocks).stream()
                .map(Block::getEncodedBlock).toList();

        simpMessagingTemplate.convertAndSend("/topic/%s".formatted(uuid), copyOfBlocks);
    }

    private Block getNewGhostBlock(Pair<Integer, Integer> coordinates) {
        var blockSizeWithBorder = environmentService.getBlockSizeWithBorder();
        Block newBlock = Block.builder().x(coordinates.getFirst()).y(coordinates.getSecond()).cells(new boolean[blockSizeWithBorder][blockSizeWithBorder]).ghostBlock(true).build();
        ghostBlocks.put(BlockUtils.getKey(newBlock.getX(), newBlock.getY()), newBlock);
        return newBlock;
    }

    public Block getBlock(int x, int y) {
        var optionalBlock = blocks.stream().filter(block -> block.getX() == x && block.getY() == y).findFirst();
        return optionalBlock.orElseGet(() -> getNewGhostBlock(Pair.of(x, y)));
    }

    public List<EncodedBlock> updateClient(ClientUpdateRequest clientUpdateRequest) {
        if (activeClients.containsKey(clientUpdateRequest.client())) {
            var clientBlocks = activeClients.get(clientUpdateRequest.client());

            clientBlocks.removeIf(block -> Set.of(clientUpdateRequest.blocksToRemove()).contains(BlockUtils.getKey(block.getX(), block.getY())));
            clientBlocks.addAll(getBlocksFromKeys(Set.of(clientUpdateRequest.blocksToAdd())));
        } else {
            throw new NotAClientException("Client not found: %s".formatted(clientUpdateRequest.client()));
        }
        return activeClients.get(clientUpdateRequest.client()).stream()
                .filter(block -> List.of(clientUpdateRequest.blocksToAdd()).contains(BlockUtils.getKey(block.getX(), block.getY())))
                .map(Block::getEncodedBlock).toList();
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
