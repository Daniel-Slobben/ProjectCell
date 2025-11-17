package slobben.Cells.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import slobben.Cells.config.BlockUpdate;
import slobben.Cells.config.StateInfo;
import slobben.Cells.controller.ClientUpdateRequest;
import slobben.Cells.entities.model.Block;
import slobben.Cells.util.BlockUtils;

import java.sql.Array;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static java.lang.Thread.sleep;

@Service
@RequiredArgsConstructor
@Slf4j
public class RunnerService {

    private final StitchingService stitchingService;
    private final BoardInfoService boardInfoService;
    private final InitializerService initializerService;
    private final GenerationService generationService;
    private final UpdateWebService updateWebService;
    private final EnvironmentService environmentService;
    ArrayList<Block> blocks;
    @Setter
    private boolean running = true;
    @Getter
    List<BlockUpdate> blockUpdates = new ArrayList<>();
    private final Map<UUID, Set<Block>> activeClients = new HashMap<>();
    private final Map<String, Block> ghostBlocks = new HashMap<>();

    @SneakyThrows
    public void run() {
        this.blocks = initializerService.initializeMap();
        do {
            long timer = System.currentTimeMillis();
            log.info("");
            log.info("Starting run!");

            // TODO: No stitch happens in first run after initializing
            stitchingService.resetStitch();
            List<Block> newBlocks = new ArrayList<>();

            forEachBlockParallel("Initialize", stitchingService::initializeStitch);
            forEachBlockParallel("Generate", (block) -> {
                // Check for block update request by user
                if (!blockUpdates.isEmpty()) {
                    var optionalBlockUpdate = blockUpdates.stream().filter(update -> update.x() == block.getX() && update.y() == block.getY()).findFirst();
                    if (optionalBlockUpdate.isPresent()) {
                        blockUpdates.remove(optionalBlockUpdate.get());
                        updateBlock(block, optionalBlockUpdate.get());
                    }
                }
                this.generationService.setNextState(block);
            });
            // create a new block for every update request that hasnt happened yet
            if (!blockUpdates.isEmpty()) {
                blockUpdates.forEach(updateBlock -> {
                    log.info("Generating new block for: x{}, y{}", updateBlock.x(), updateBlock.y());
                    boolean[][] matrix = new boolean[environmentService.getBlockSizeWithBorder()][environmentService.getBlockSizeWithBorder()];

                    Block newBlock;
                    var key = BlockUtils.getKey(updateBlock.x(), updateBlock.y());
                    if (ghostBlocks.containsKey(key)) {
                        var ghostBlock = ghostBlocks.get(key);
                        ghostBlock.setGhostBlock(false);
                        ghostBlocks.remove(key);
                        newBlock = ghostBlock;
                    } else {
                        newBlock = new Block(updateBlock.x(), updateBlock.y(), matrix);
                    }

                    stitchingService.initializeStitch(newBlock);
                    updateBlock(newBlock, updateBlock);
                    blocks.add(newBlock);
                });
                blockUpdates.clear();
            }

            forEachBlockParallel("AddBorderCells", block -> newBlocks.addAll(stitchingService.addBorderCells(block)));
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

            activeClients.forEach(updateWebService::updateClient);
            forEachBlockParallel("Stitch", stitchingService::stitchBlock);

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

    private void updateBlock(Block block, BlockUpdate update) {
        for (int x = 1; x < update.state().length + 1; x++) {
            System.arraycopy(update.state()[x - 1], 0, block.getCells()[x], 1, update.state().length);
        }
    }

    private void forEachBlockParallel(String taskName, Consumer<Block> task) throws InterruptedException {
        forEachParallel(taskName, blocks, task);
    }

    private void forEachParallel(String taskName, List<Block> blocks, Consumer<Block> task) throws InterruptedException {
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
        return StateInfo.builder()
                .blocksInMemory(blocks.size())
                .blocksUpdating((int)(blocks.stream().filter(Block::isUpdatingWeb).count()))
                .build();
    }

    public void setBlock(int x, int y, boolean[][] body) {
        blockUpdates.add(BlockUpdate.builder().x(x).y(y).state(body).build());
    }

    public Set<Block> getBlocksFromKeys(Set<String> keys) {
        Set<Block> blocksToAdd = new HashSet<>();
        for (String key : keys) {
            var coordinates = BlockUtils.resolveKey(key);
            try {
                blocksToAdd.add(getBlock(coordinates.getFirst(), coordinates.getSecond()));
            } catch (IllegalStateException e) {
                Block newBlock = getNewGhostBlock(coordinates);
                blocksToAdd.add(newBlock);
            }
        }
        return blocksToAdd;
    }

    public List<Block> updateClient(ClientUpdateRequest clientUpdateRequest) {
        if (activeClients.containsKey(clientUpdateRequest.client())) {
            var clientBlocks = activeClients.get(clientUpdateRequest.client());

            clientBlocks.removeIf(block -> Set.of(clientUpdateRequest.blocksToRemove()).contains(BlockUtils.getKey(block.getX(), block.getY())));
            clientBlocks.addAll(getBlocksFromKeys(Set.of(clientUpdateRequest.blocksToAdd())));
        } else {
           activeClients.put(clientUpdateRequest.client(), getBlocksFromKeys(Set.of(clientUpdateRequest.blocksToAdd())));
        }
        return activeClients.get(clientUpdateRequest.client()).stream().toList();
    }

    private Block getNewGhostBlock(Pair<Integer, Integer> coordinates) {
        var blockSizeWithBorder = environmentService.getBlockSizeWithBorder();
        Block newBlock = Block.builder()
                .x(coordinates.getFirst())
                .y(coordinates.getSecond())
                .cells(new boolean[blockSizeWithBorder][blockSizeWithBorder])
                .ghostBlock(true)
                .build();
        ghostBlocks.put(BlockUtils.getKey(newBlock.getX(), newBlock.getY()), newBlock);
        return newBlock;
    }
}
