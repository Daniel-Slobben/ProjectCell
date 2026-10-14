package slobben.cells.service.worker;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import slobben.cells.entities.Block;
import slobben.cells.service.ExecutorService;
import slobben.cells.service.workers.AlgorithmGol;

import java.util.HashMap;
import java.util.UUID;

import static slobben.cells.util.BlockUtils.setBlockToRandom;

class AlgorithmGolLoadTest {

    private static final long CELL_UPDATES = 2_000_000_000;
    private static final int MAX_BLOCK_AGE = 200;

    @ParameterizedTest
    @Disabled("Too expensive for normal test run")
    @ValueSource(ints = {200, 300, 900, 1000, 1100, 2000})
    void profileRandom(int blockSize) {
        profile(algorithmGol(blockSize, 50), blockSize);
    }

    @ParameterizedTest
    @Disabled("Too expensive for normal test run")
    @ValueSource(ints = {5, 10, 20, 50, 100, 200})
    void profileSector(int sectorSize) {
        profile(algorithmGol(2000, sectorSize), 2000);
    }

    private static void profile(AlgorithmGol algorithmGol, int blockSize) {
        Block block = null;
        for (int i = 0; i < CELL_UPDATES / ((long) blockSize * blockSize); i++) {
            if (i % MAX_BLOCK_AGE == 0) {
                block = new Block(0, 0, UUID.randomUUID(), blockSize);
                setBlockToRandom(block, 6, blockSize);
            }
            algorithmGol.setNextState(block);
        }
    }

    static AlgorithmGol algorithmGol(int blockSize, int sectorSize) {
        AlgorithmGol algorithmGol = new AlgorithmGol(new ExecutorService(), new HashMap<>());
        ReflectionTestUtils.setField(algorithmGol, "blockSize", blockSize);
        ReflectionTestUtils.setField(algorithmGol, "sectorSize", sectorSize);
        ReflectionTestUtils.invokeMethod(algorithmGol, "init");
        return algorithmGol;
    }
}
