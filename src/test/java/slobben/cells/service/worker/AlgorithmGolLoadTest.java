package slobben.cells.service.worker;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import slobben.cells.entities.Block;
import slobben.cells.service.worker.gol.ProductionGol;
import slobben.cells.service.workers.AlgorithmGolWithSectors;

import java.util.UUID;

import static slobben.cells.util.BlockUtils.setBlockToRandom;

/**
 * Profiling entry points (attach a profiler). For A/B numbers see gol.GolAlgorithmAbLoadTest.
 */
class AlgorithmGolLoadTest {

    private static final long CELL_UPDATES = 2_000_000_000;
    private static final int MAX_BLOCK_AGE = 200;

    @ParameterizedTest
    @Disabled("Too expensive for normal test run")
    @ValueSource(ints = {200, 300, 900, 1000, 1100, 2000})
    void profileRandom(int blockSize) {
        profile(ProductionGol.create(blockSize, 50), blockSize);
    }

    @ParameterizedTest
    @Disabled("Too expensive for normal test run")
    @ValueSource(ints = {5, 10, 20, 50, 100, 200})
    void profileSector(int sectorSize) {
        profile(ProductionGol.create(2000, sectorSize), 2000);
    }

    private static void profile(AlgorithmGolWithSectors algorithmGol, int blockSize) {
        Block block = null;
        for (int i = 0; i < CELL_UPDATES / ((long) blockSize * blockSize); i++) {
            if (i % MAX_BLOCK_AGE == 0) {
                block = new Block(0, 0, UUID.randomUUID(), blockSize);
                setBlockToRandom(block, 6, blockSize);
            }
            algorithmGol.setNextState(block);
        }
    }
}
