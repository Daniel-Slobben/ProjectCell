package slobben.Cells.service;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import slobben.Cells.entities.model.Block;
import slobben.Cells.entities.model.Cell;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

            ExecutorService executor = Executors.newFixedThreadPool(24);
            // TODO: No stitch happens in first run after initializing
            for (Integer blockKeyX : blocks.keySet()) {
                for (Integer blockKeyY : blocks.get(blockKeyX).keySet()) {
                    executor.execute(() -> {
                        Block block = blocks.get(blockKeyX).get(blockKeyY);
                        generationService.setNextState(block);
                        stitchingService.addBorderCells(block);
                        updateWebService.updateBlock(block);
                    });
                }
            }
            executor.shutdown();
            executor.awaitTermination(60, TimeUnit.SECONDS);
            executor = Executors.newFixedThreadPool(24);

            for (Integer blockKeyX : blocks.keySet()) {
                for (Integer blockKeyY : blocks.get(blockKeyX).keySet()) {
                    executor.execute(() -> {
                        Block block = blocks.get(blockKeyX).get(blockKeyY);
                        stitchingService.stitchBlock(block);
                    });
                }
            }
            executor.shutdown();
            executor.awaitTermination(60, TimeUnit.SECONDS);
            stitchingService.initializeStich();
            long timeTaken = System.currentTimeMillis() - timer;
            long timeDelta = timeTaken - environmentService.getTargetspeed();
            if (timeDelta < 0) {
                Thread.sleep(Math.abs(timeDelta));
                log.info("Ending run. Time Taken: {}ms, Waited for {}ms", timeTaken, Math.abs(timeDelta));
            } else {
                log.info("Ending run. Time Taken: {}ms, No waiting!", timeTaken);
            }
        }
    }

    public Cell[][] getBlock(int x, int y) {
        return boardInfoService.getBlock(blocks.get(x).get(y));
    }

    public Cell[][] getBlockWithoutBorders(int x, int y) {
        return boardInfoService.getBlockWithoutBorder(blocks.get(x).get(y));
    }
}
