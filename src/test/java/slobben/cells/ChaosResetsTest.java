package slobben.cells;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import slobben.cells.entities.Block;
import slobben.cells.service.RunnerService;
import slobben.cells.service.workers.LoopService;

import java.lang.management.ManagementFactory;
import java.util.Map;

/**
 * Runs the seeded chaos hits of the local profile uncapped until the world resets a number of times and reports blocks, hibernating blocks,
 * heap and tick time. Run with: mvn test -Dtest=ChaosResetsTest -Djunit.jupiter.conditions.deactivate='org.junit.*DisabledCondition'
 * -DargLine="-Xmx8g" -Dresets=2, and with -Dcells.loops.check-every=1000000000 for the same run without hibernation.
 */
@SpringBootTest(properties = {
        "cells.size.blockSize=2000",
        "cells.size.sectorSize=50",
        "cells.pruning-per-generation=100",
        "cells.runmode=MANUAL",
        "cells.targetspeed=0",
        "cells.chaos.type=ANY",
        "cells.chaos.max-blocks=2000",
        "logging.level.slobben=WARN",
        "logging.level.slobben.cells.service.workers.ChaosService=INFO"
})
@ActiveProfiles("local")
class ChaosResetsTest {

    private static final int RESETS = Integer.getInteger("resets", 2);
    private static final int REPORT_EVERY = 500;

    @Autowired
    private RunnerService runnerService;
    @Autowired
    private Map<String, Block> blocks;
    @Autowired
    private LoopService loopService;

    @Test
    @Disabled("Runs for many minutes")
    @SuppressWarnings("java:S2699")
    void chaosResets() {
        long start = System.currentTimeMillis();
        long tickNanos = 0, cycleNanos = 0, cycleTicks = 0, peakHeap = 0;
        int resets = 0, before = blocks.size(), peakBlocks = 0, peakHibernating = 0;
        for (int tick = 1; resets < RESETS; tick++) {
            long tickStart = System.nanoTime();
            runnerService.runCycle();
            long nanos = System.nanoTime() - tickStart;
            tickNanos += nanos;
            cycleNanos += nanos;
            cycleTicks++;
            peakBlocks = Math.max(peakBlocks, blocks.size());
            peakHibernating = Math.max(peakHibernating, loopService.getHibernating());
            if (blocks.size() < before / 2) {
                resets++;
                System.out.printf("RESET %d at tick %d (%.1f min): %d ticks at %.1f ms, peak %d blocks, %d hibernating, heap %d MB%n",
                        resets, tick, (System.currentTimeMillis() - start) / 60_000.0, cycleTicks, cycleNanos / 1e6 / cycleTicks,
                        peakBlocks, peakHibernating, peakHeap >> 20);
                cycleNanos = cycleTicks = peakHeap = peakBlocks = peakHibernating = 0;
            }
            before = blocks.size();
            if (tick % REPORT_EVERY == 0) {
                System.gc();
                long heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
                peakHeap = Math.max(peakHeap, heap);
                System.out.printf("tick %d (%.1f min, %.1f ms per tick): %d blocks, %d hibernating, heap %d MB after GC (%.2f MB per block)%n",
                        tick, (System.currentTimeMillis() - start) / 60_000.0, tickNanos / 1e6 / REPORT_EVERY, blocks.size(),
                        loopService.getHibernating(), heap >> 20, (double) heap / (1 << 20) / Math.max(1, blocks.size()));
                tickNanos = 0;
            }
        }
    }
}
