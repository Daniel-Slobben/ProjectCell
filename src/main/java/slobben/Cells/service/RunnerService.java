package slobben.Cells.service;

import com.mongodb.lang.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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

    @SneakyThrows
    public void run() {
        this.blocks = initializerService.initializeMap();
        while (running) {
            long timer = System.currentTimeMillis();
            log.info("");
            log.info("Starting run!");

            // TODO: No stitch happens in first run after initializing
            stitchingService.resetStitch();
            List<Block> newBlocks = new ArrayList<>();

            forEachBlockParallel("Initialize", stitchingService::initializeStitch);
            forEachBlockParallel("Generate", generationService::setNextState);

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
        }
    }

    public boolean[][] getBlockWithoutBorders(int x, int y) {
        setBlockUpdate(x, y, true);
        return boardInfoService.getBlockWithoutBorder(getBlock(x, y));
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

    public boolean setBlockUpdate(int x, int y, boolean update) {
        var block = getBlock(x, y);
        var originalValue = block.isUpdatingWeb();
        block.setUpdatingWeb(update);
        return originalValue;
    }

    public StateInfo getStateInfo() {
        return StateInfo.builder()
                .blocksInMemory(blocks.size())
                .blocksUpdating((int)(blocks.stream().filter(Block::isUpdatingWeb).count()))
                .build();
    }
}
