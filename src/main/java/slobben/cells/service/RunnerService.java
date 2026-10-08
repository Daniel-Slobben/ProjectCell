package slobben.cells.service;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import slobben.cells.entities.Block;
import slobben.cells.service.workers.*;
import slobben.cells.service.workers.chaos.ChaosService;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RunnerService {

    private final ChaosService chaosService;
    private final AddNewBlocks addNewBlocks;
    private final AlgorithmGol algorithmGol;
    private final Stitching stitching;
    private final Pruning pruning;
    private final Packer packingService;
    private final ClientService clientService;

    private final Map<String, Block> blocks;

    @Value("${cells.targetspeed}")
    private int targetSpeed;
    @Value("${cells.runmode}")
    private String runMode;

    private boolean running = false;

    private int ticks = 0;

    public void runCycle() {
        algorithmGol.tic();

        chaosService.tic();
        addNewBlocks.tic();

        stitching.tic();
        addNewBlocks.tic();

        packingService.tic();
        clientService.tic();

        pruning.tic();
    }

    @SneakyThrows
    public void run() {
        running = runMode.equals("AUTO");
        while (running) {
            ticks++;
            log.info("Starting run {} with {} amount of blocks in memory", ticks, blocks.size());
            long timer = System.currentTimeMillis();

            runCycle();

            long timeTaken = System.currentTimeMillis() - timer;
            long timeDelta = timeTaken - targetSpeed;
            if (timeDelta < 0) {
                Thread.sleep(Math.abs(timeDelta));
                log.info("Ending run. Time Taken: {}ms, Waited for {}ms\n", timeTaken, Math.abs(timeDelta));
            } else {
                log.info("Ending run. Time Taken: {}ms, No waiting!\n", timeTaken);
            }
        }
    }

    @PreDestroy
    public void destroy() {
        running = false;
    }
}
