package slobben.Cells.service;

import com.mongodb.lang.Nullable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import slobben.Cells.config.BlockUpdate;
import slobben.Cells.config.StateInfo;
import slobben.Cells.entities.model.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
    @Setter
    private boolean running = true;
    ArrayList<Block> blocks;
    @Getter
    List<BlockUpdate> blockUpdates = new ArrayList<>();

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
                if (!blockUpdates.isEmpty()) {
                    var optionalBlockUpdate = blockUpdates.stream().filter(update -> update.x() == block.getX() && update.y() == block.getY()).findFirst();
                    if (optionalBlockUpdate.isPresent()) {
                        blockUpdates.remove(optionalBlockUpdate.get());
                        updateBlock(block, optionalBlockUpdate.get());
                        return;
                    }
                }
                generationService.setNextState(block);
            });

            forEachBlockParallel("AddBorderCells", block -> newBlocks.addAll(stitchingService.addBorderCells(block)));
            blocks.addAll(newBlocks);
            forEachParallel("WebUpdate", blocks.stream().filter(Block::isUpdatingWeb).toList(), updateWebService::updateBlock);
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

    private Block getBlock(int x, int y) {
        var optionalBlock = blocks.stream().filter(block -> block.getX() == x && block.getY() == y).findFirst();
        if (optionalBlock.isEmpty()) {
            throw new IllegalStateException("No block found for x: " + x + ", y: " + y);
        }
        return optionalBlock.get();
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

    public boolean[][] setBlockUpdate(int x, int y, boolean update) {
        var block = getBlock(x, y);
        block.setUpdatingWeb(update);
        return boardInfoService.getBlockWithoutBorder(block);
    }

    public StateInfo getStateInfo() {
        return StateInfo.builder()
                .blocksInMemory(blocks.size())
                .blocksUpdating((int)(blocks.stream().filter(Block::isUpdatingWeb).count()))
                .build();
    }
}
