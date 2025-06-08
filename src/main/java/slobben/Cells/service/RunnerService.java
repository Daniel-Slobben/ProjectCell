package slobben.Cells.service;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import slobben.Cells.entities.model.Block;
import slobben.Cells.entities.model.Cell;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

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
    private ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, Block>> blocks = new ConcurrentHashMap<>();


    @SneakyThrows
    public void run() {
        this.blocks = initializerService.initializeMap();
        while (running) {
            long timer = System.currentTimeMillis();
            log.info("Starting run!");

            // TODO: No stitch happens in first run after initializing
            stitchingService.resetStitch();
            List<Block> newBlocks = new ArrayList<>();

            forEachBlockParallel(24, stitchingService::initializeStitch);

            forEachBlockParallel(24, block -> {
                generationService.setNextState(block);
                newBlocks.addAll(stitchingService.addBorderCells(block));
                updateWebService.updateBlock(block);
            });

            for (Block newBlock : newBlocks) {
                blocks.computeIfAbsent(newBlock.getX(), row -> new ConcurrentHashMap<>()).put(newBlock.getY(), newBlock);
            }

            forEachBlockParallel(24, stitchingService::stitchBlock);

            long timeTaken = System.currentTimeMillis() - timer;
            long timeDelta = timeTaken - environmentService.getTargetspeed();
            if (timeDelta < 0) {
                wait(Math.abs(timeDelta));
                log.info("Ending run. Time Taken: {}ms, Waited for {}ms", timeTaken, Math.abs(timeDelta));
            } else {
                log.info("Ending run. Time Taken: {}ms, No waiting!", timeTaken);
            }
        }
    }

    public Cell[][] getBlockWithoutBorders(int x, int y) {
        return boardInfoService.getBlockWithoutBorder(blocks.get(x).get(y));
    }

    private void forEachBlockParallel(int threadCount, Consumer<Block> task) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        blocks.forEach((blockKeyX, row) -> row.forEach((blockKeyY, block) -> executor.execute(() -> task.accept(block))));

        executor.shutdown();
        if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
            log.warn("Executor did not shut down cleanly within timeout.");
        }
    }
}
