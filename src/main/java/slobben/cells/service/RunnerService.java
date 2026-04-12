package slobben.cells.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import slobben.cells.dto.BlockUpdate;
import slobben.cells.dto.StateInfo;
import slobben.cells.entities.model.Block;
import slobben.cells.util.BlockUtils;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
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
    private final ChaosService chaosService;
    private final EnvironmentService environmentService;
    private final PruningService pruningService;

    private final Set<Block> blocks;
    private final Map<String, Block> ghostBlocks;
    private final ClientService clientService;

    private int generation = 0;

    @Getter
    List<BlockUpdate> blockUpdates = new ArrayList<>();
    @Setter
    private boolean running = true;

    @Value("${properties.pruning-per-generation}")
    private int pruningPerGeneration;

    @Value("${properties.threads}")
    private int threads;

    @SneakyThrows
    public void run() {
        do {
            long timer = System.currentTimeMillis();
            generation++;
            log.info("");
            log.info("Starting run {} with {} amount of blocks in memory", generation, blocks.size());

            stitchingService.resetStitch();

            if (generation % pruningPerGeneration == 0) {
               pruningService.pruneBlocks(blocks);
            }

            forEachBlockParallel("Initialize", stitchingService::initializeStitch);
            forEachBlockParallel("Generate", GenerationService::setNextState);

            blockUpdates.addAll(chaosService.tic());
            checkForExternalBlockUpdates();

            List<Block> newBlocks = new CopyOnWriteArrayList<>();
            forEachBlockParallel("AddBorderCells", block -> newBlocks.addAll(stitchingService.addBorderCells(block)));
            createNewBlocks(newBlocks);
            forEachBlockParallel("Stitch", stitchingService::stitchBlock);

            clientService.tic();

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
            if (optionalBlock.isPresent()) {
                updateBlock(optionalBlock.get(), blockUpdate);
            }
            else {
                createBlock(blockUpdate);
            }
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
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        blocks.forEach(block -> executor.execute(() -> task.accept(block)));

        executor.shutdown();
        if (!executor.awaitTermination(120, TimeUnit.SECONDS)) {
            log.warn("Executor did not shut down cleanly within timeout.");
        }
        log.info("Task {} finished in: {}ms", taskName, System.currentTimeMillis() - timer);
    }


    public StateInfo getStateInfo() {
        return StateInfo.builder().blocksInMemory(blocks.size()).blocksUpdating((int) (blocks.stream().filter(Block::isUpdatingWeb).count())).build();
    }

    public void setBlock(int x, int y, boolean[][] body) {
        blockUpdates.add(BlockUpdate.builder().x(x).y(y).state(body).build());
    }

}
