package slobben.cells.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import slobben.cells.config.BlockUpdate;
import slobben.cells.config.StateInfo;
import slobben.cells.controller.ClientUpdateRequest;
import slobben.cells.entities.model.Block;
import slobben.cells.entities.model.EncodedBlock;
import slobben.cells.util.BlockUtils;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

import static java.lang.Thread.sleep;

@Service
@RequiredArgsConstructor
@Slf4j
public class RunnerService {

    private final StitchingService stitchingService;
    private final UpdateWebService updateWebService;
    private final EnvironmentService environmentService;
    private final Map<UUID, ConcurrentLinkedQueue<Block>> activeClients = new ConcurrentHashMap<>();
    private final Map<String, Block> ghostBlocks = new HashMap<>();
    Set<Block> blocks;
    @Getter
    List<BlockUpdate> blockUpdates = new ArrayList<>();
    @Setter
    private boolean running = true;

    @SneakyThrows
    public void run() {
        if (environmentService.getRunMode().equals("MANUAL") || environmentService.getSetupMode().equals("EMPTY")) {
            this.blocks = InitializerService.getEmptyMap();
        } else {
            this.blocks = InitializerService.getRandomMap();
        }
        do {
            long timer = System.currentTimeMillis();
            log.info("");
            log.info("Starting run!");

            stitchingService.resetStitch();

            forEachBlockParallel("Initialize", stitchingService::initializeStitch);
            forEachBlockParallel("Generate", GenerationService::setNextState);
            checkForExternalBlockUpdates();

            List<Block> newBlocks = new ArrayList<>();
            forEachBlockParallel("AddBorderCells", block -> newBlocks.addAll(stitchingService.addBorderCells(block)));
            createNewBlocks(newBlocks);
            forEachBlockParallel("Stitch", stitchingService::stitchBlock);

            activeClients.forEach(updateWebService::updateClient);

            long timeTaken = System.currentTimeMillis() - timer;
            long timeDelta = timeTaken - environmentService.getTargetspeed();
            if (timeDelta < 0) {
                sleep(Math.abs(timeDelta));
                log.info("Ending run. Time Taken: {}ms, Waited for {}ms", timeTaken, Math.abs(timeDelta));
            } else {
                log.info("Ending run. Time Taken: {}ms, No waiting!", timeTaken);
            }
        } while (running);
    }

    private void createNewBlocks(List<Block> newBlocks) {
        var adjustedNewBlocks = newBlocks.stream().map(block -> {
            var key = BlockUtils.getKey(block.getX(), block.getY());
            if (ghostBlocks.containsKey(key)) {
                var ghostBlock = ghostBlocks.get(key);
                ghostBlock.setGhostBlock(false);
                ghostBlocks.remove(key);
                return ghostBlock;
            }
            return block;
        }).toList();
        blocks.addAll(adjustedNewBlocks);
    }

    private void checkForExternalBlockUpdates() {
        for (BlockUpdate blockUpdate : blockUpdates) {
            Optional<Block> optionalBlock = blocks.stream().filter(block -> block.getX() == blockUpdate.x() && block.getY() == blockUpdate.y()).findFirst();
            if (optionalBlock.isPresent()) updateBlock(optionalBlock.get(), blockUpdate);
            else createBlock(blockUpdate);
        }
        blockUpdates.clear();
    }

    private void updateBlock(Block block, BlockUpdate update) {
        for (int x = 1; x < update.state().length + 1; x++) {
            System.arraycopy(update.state()[x - 1], 0, block.getCells()[x], 1, update.state().length);
        }
    }

    private void createBlock(BlockUpdate blockUpdate) {
        log.info("Generating new block for: x{}, y{}", blockUpdate.x(), blockUpdate.y());
        boolean[][] matrix = new boolean[environmentService.getBlockSizeWithBorder()][environmentService.getBlockSizeWithBorder()];

        Block newBlock;
        var key = BlockUtils.getKey(blockUpdate.x(), blockUpdate.y());
        if (ghostBlocks.containsKey(key)) {
            var ghostBlock = ghostBlocks.get(key);
            ghostBlock.setGhostBlock(false);
            ghostBlocks.remove(key);
            newBlock = ghostBlock;
        } else {
            newBlock = new Block(blockUpdate.x(), blockUpdate.y(), matrix);
        }

        stitchingService.initializeStitch(newBlock);
        updateBlock(newBlock, blockUpdate);
        blocks.add(newBlock);
    }

    private void forEachBlockParallel(String taskName, Consumer<Block> task) throws InterruptedException {
        forEachParallel(taskName, blocks, task);
    }

    private void forEachParallel(String taskName, Set<Block> blocks, Consumer<Block> task) throws InterruptedException {
        long timer = System.currentTimeMillis();
        ExecutorService executor = Executors.newFixedThreadPool(16);

        blocks.forEach(block -> executor.execute(() -> task.accept(block)));

        executor.shutdown();
        if (!executor.awaitTermination(120, TimeUnit.SECONDS)) {
            log.warn("Executor did not shut down cleanly within timeout.");
        }
        log.info("Task {} finished in: {}ms", taskName, System.currentTimeMillis() - timer);
    }

    public Block getBlock(int x, int y) {
        var optionalBlock = blocks.stream().filter(block -> block.getX() == x && block.getY() == y).findFirst();
        return optionalBlock.orElseGet(() -> getNewGhostBlock(Pair.of(x, y)));
    }

    public StateInfo getStateInfo() {
        return StateInfo.builder().blocksInMemory(blocks.size()).blocksUpdating((int) (blocks.stream().filter(Block::isUpdatingWeb).count())).build();
    }

    public void setBlock(int x, int y, boolean[][] body) {
        blockUpdates.add(BlockUpdate.builder().x(x).y(y).state(body).build());
    }

    public Set<Block> getBlocksFromKeys(Set<String> keys) {
        Set<Block> blocksToAdd = new HashSet<>();
        for (String key : keys) {
            var coordinates = BlockUtils.resolveKey(key);
            blocksToAdd.add(getBlock(coordinates.getFirst(), coordinates.getSecond()));
        }
        return blocksToAdd;
    }

    public List<EncodedBlock> updateClient(ClientUpdateRequest clientUpdateRequest) {
        if (activeClients.containsKey(clientUpdateRequest.client())) {
            var clientBlocks = activeClients.get(clientUpdateRequest.client());

            clientBlocks.removeIf(block -> Set.of(clientUpdateRequest.blocksToRemove()).contains(BlockUtils.getKey(block.getX(), block.getY())));
            clientBlocks.addAll(getBlocksFromKeys(Set.of(clientUpdateRequest.blocksToAdd())));
        } else {
            ConcurrentLinkedQueue<Block> blocksToAdd = new ConcurrentLinkedQueue<>(getBlocksFromKeys(Set.of(clientUpdateRequest.blocksToAdd())));
            activeClients.put(clientUpdateRequest.client(), blocksToAdd);
        }
        return activeClients.get(clientUpdateRequest.client()).stream().map(Block::getEncodedBlock).toList();
    }

    private Block getNewGhostBlock(Pair<Integer, Integer> coordinates) {
        var blockSizeWithBorder = environmentService.getBlockSizeWithBorder();
        Block newBlock = Block.builder().x(coordinates.getFirst()).y(coordinates.getSecond()).cells(new boolean[blockSizeWithBorder][blockSizeWithBorder]).ghostBlock(true).build();
        ghostBlocks.put(BlockUtils.getKey(newBlock.getX(), newBlock.getY()), newBlock);
        return newBlock;
    }
}
