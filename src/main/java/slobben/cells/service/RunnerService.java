package slobben.cells.service;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import slobben.cells.entities.model.Block;
import slobben.cells.service.workers.*;

import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class RunnerService {

    private final BorderService borderService;
    private final ChaosService chaosService;
    private final PruningService pruningService;
    private final NewBlockService newBlockService;
    private final StitchingService stitchingService;
    private final ClientService clientService;
    private final GenerationService generationService;

    private final Set<Block> blocks;

    @Value("${cells.targetspeed}")
    private int targetSpeed;
    @Value("${cells.runmode}")
    private String runMode;

    private boolean running = false;

    private int generation = 0;

    public void runCycle() {
        pruningService.tic();
        generationService.tic();
        chaosService.tic();
        newBlockService.tic();
        borderService.tic();
        stitchingService.tic();
        clientService.tic();
    }

    @SneakyThrows
    @Scheduled
    public void run() {
        running = runMode.equals("AUTO");
        while (running) {
            generation++;
            log.info("\nStarting run {} with {} amount of blocks in memory", generation, blocks.size());
            long timer = System.currentTimeMillis();

            runCycle();

            long timeTaken = System.currentTimeMillis() - timer;
            long timeDelta = timeTaken - targetSpeed;
            if (timeDelta < 0) {
                Thread.sleep(Math.abs(timeDelta));
                log.info("Ending run. Time Taken: {}ms, Waited for {}ms", timeTaken, Math.abs(timeDelta));
            } else {
                log.info("Ending run. Time Taken: {}ms, No waiting!", timeTaken);
            }
        }
    }

    @PreDestroy
    public void destroy() {
        running = false;
    }
}
