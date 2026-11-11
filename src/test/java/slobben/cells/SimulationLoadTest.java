package slobben.cells;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import slobben.cells.dto.internal.Coordinates;
import slobben.cells.entities.Block;
import slobben.cells.service.RunnerService;
import slobben.cells.service.ServerInfoService;
import slobben.cells.service.workers.ChaosService;
import slobben.cells.service.workers.chaosmakers.SquareMaker;
import slobben.cells.util.PackedRows;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Runs the full cycle on the same chaos hits every time, so runs of different versions can be compared
 * on both speed and outcome. Run with: mvn test -Dtest=SimulationLoadTest -DargLine=-Xmx12g
 */
@SpringBootTest(properties = {
        "cells.size.blockSize=2000",
        "cells.size.sectorSize=50",
        "cells.pruning-per-generation=100",
        "cells.runmode=MANUAL",
        "cells.targetspeed=0",
        "logging.level.slobben=WARN"
})
class SimulationLoadTest {

    private static final int GENERATIONS = 3000;
    private static final int HIT_SIZE = 50000;
    private static final List<Coordinates> HITS = List.of(new Coordinates(0, 0));

    @MockitoBean
    private ChaosService chaosService;

    @Autowired
    private RunnerService runnerService;
    @Autowired
    private ServerInfoService serverInfoService;
    @Autowired
    private SquareMaker squareMaker;
    @Autowired
    private Map<String, Block> blocks;
    @Value("${cells.size.blockSize}")
    private int blockSize;

    @Test
    @Disabled("Too expensive for normal test run")
    @SuppressWarnings("java:S2699")
    void squareHits() {

        HITS.forEach(hit -> squareMaker.getChaosHit(hit.x(), hit.y(), HIT_SIZE));

        long start = System.nanoTime();
        for (int generation = 1; generation <= GENERATIONS; generation++) {
            runnerService.runCycle();
            if (generation % 100 == 0) {
                System.out.printf("generation %d: %d blocks, %dms%n", generation, blocks.size(), (System.nanoTime() - start) / 1_000_000);
            }
        }
        long millis = (System.nanoTime() - start) / 1_000_000;

        System.out.printf("RESULT %d generations in %dms (%.1fms/gen), %s%n", GENERATIONS, millis, (double) millis / GENERATIONS, fingerprint());
        serverInfoService.getAverageWorkTimes().forEach((worker, ms) -> System.out.printf("WORKER %-30s %.3fms%n", worker, ms));
    }

    // the inner cells only, so versions that handle the borders differently still compare
    private String fingerprint() {
        long liveCells = 0;
        long hash = 1;
        for (Block block : new TreeMap<>(blocks).values()) {
            long[][] packed = block.getPackedCells();
            for (int x = 1; x <= blockSize; x++) {
                for (int y = 1; y <= blockSize; y++) {
                    if (!PackedRows.get(packed[x], y)) continue;
                    liveCells++;
                    hash = hash * 31 + (((long) block.getX() * blockSize + x) << 32 | (((long) block.getY() * blockSize + y) & 0xffffffffL));
                }
            }
        }
        return "blocks=%d liveCells=%d hash=%x".formatted(blocks.size(), liveCells, hash);
    }
}
