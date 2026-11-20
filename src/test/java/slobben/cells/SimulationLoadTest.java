package slobben.cells;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import slobben.cells.dto.internal.Coordinates;
import slobben.cells.entities.Block;
import slobben.cells.entities.Loop;
import slobben.cells.enums.BlockState;
import slobben.cells.service.RunnerService;
import slobben.cells.service.ServerInfoService;
import slobben.cells.service.workers.ChaosService;
import slobben.cells.service.workers.chaosmakers.DiagonalMaker;
import slobben.cells.service.workers.chaosmakers.LettuceMaker;
import slobben.cells.service.workers.chaosmakers.SquareMaker;
import slobben.cells.util.PackedRows;

import java.lang.management.ManagementFactory;
import java.util.*;

/**
 * Runs the full cycle on the same chaos hits every time, so runs of different versions can be compared
 * on both speed and outcome. Run with: mvn test -Dtest=SimulationLoadTest -DargLine=-Xmx12g
 * With -Dsimulation.chaosHits=N it starts from N seeded hits of the chaos sizes and types instead.
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

    private static final int GENERATIONS = Integer.getInteger("simulation.generations", 3000);
    private static final int CHAOS_HITS = Integer.getInteger("simulation.chaosHits", 0);
    private static final int GRID_SPACING = 24000;
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
    private LettuceMaker lettuceMaker;
    @Autowired
    private DiagonalMaker diagonalMaker;
    @Autowired
    private Map<String, Block> blocks;
    @Value("${cells.size.blockSize}")
    private int blockSize;

    @Test
    @Disabled("Too expensive for normal test run")
    @SuppressWarnings("java:S2699")
    void squareHits() {

        if (CHAOS_HITS == 0) {
            HITS.forEach(hit -> squareMaker.getChaosHit(hit.x(), hit.y(), HIT_SIZE));
        } else {
            addChaosHits(new Random(42));
        }

        long start = System.nanoTime();
        for (int generation = 1; generation <= GENERATIONS; generation++) {
            runnerService.runCycle();
            if (generation % 100 == 0) {
                System.out.printf("generation %d: %d blocks, %d hibernating, %dms%n", generation, blocks.size(), hibernating(), (System.nanoTime() - start) / 1_000_000);
            }
        }
        long millis = (System.nanoTime() - start) / 1_000_000;

        System.out.printf("RESULT %d generations in %dms (%.1fms/gen), %s%n", GENERATIONS, millis, (double) millis / GENERATIONS, getFingerprint());
        serverInfoService.getAverageWorkTimes().forEach((worker, ms) -> System.out.printf("WORKER %-30s %.3fms%n", worker, ms));
        System.gc();
        System.gc();
        List<Loop> loops = blocks.values().stream().map(Block::getLoop).filter(Objects::nonNull).toList();
        long loopBytes = loops.stream().flatMap(loop -> loop.phases().stream()).mapToLong(SimulationLoadTest::bytes).sum();
        System.out.printf("LOOPS %d of %d blocks hibernating, %.1f phases and %d KB per hibernating block%n", loops.size(), blocks.size(),
                loops.stream().mapToInt(loop -> loop.phases().size()).average().orElse(0), loops.isEmpty() ? 0 : loopBytes / loops.size() >> 10);
        System.out.printf("HEAP %d MB after GC%n", ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed() >> 20);
    }

    private static long bytes(Loop.Phase phase) {
        return phase.full().length + phase.border().length + phase.sectors().length
                + Arrays.stream(phase.sectorCounts()).mapToLong(band -> 2L * band.length).sum()
                + bytes(phase.edges()) + bytes(phase.borders());
    }

    private static long bytes(Loop.Outline outline) {
        return 8L * (outline.top().length + outline.bottom().length + outline.left().length + outline.right().length);
    }

    private long hibernating() {
        return blocks.values().stream().filter(block -> block.getBlockState() == BlockState.HIBERNATION).count();
    }

    private void addChaosHits(Random random) {
        int columns = (int) Math.ceil(Math.sqrt(CHAOS_HITS));
        for (int i = 0; i < CHAOS_HITS; i++) {
            int size = 2000 + (int) (Math.pow(random.nextDouble(), 3) * 18000);
            int x = i / columns * GRID_SPACING, y = i % columns * GRID_SPACING;
            switch (i % 3) {
                case 0 -> squareMaker.getChaosHit(x, y, size);
                case 1 -> lettuceMaker.getChaosHit(x, y, size, random.nextInt(4, 13));
                default -> diagonalMaker.getChaosHit(x, y, size);
            }
        }
    }

    // the inner cells only, so versions that handle the borders differently still compare
    private String getFingerprint() {
        long liveCells = 0;
        long hash = 1;
        for (Block block : new TreeMap<>(blocks).values()) {
            long[][] packed = TestUtils.getCells(block, blockSize);
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
