package slobben.cells.service.worker;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import slobben.cells.entities.Block;
import slobben.cells.service.workers.AlgorithmGolWithSectors;

import java.util.UUID;

import static slobben.cells.util.BlockUtils.setBlockToRandom;

@ExtendWith(MockitoExtension.class)
@RequiredArgsConstructor
class AlgorithmGolLoadTest {

    private static final long CELL_UPDATES = 2_000_000_000;
    private static final int MAX_BLOCK_AGE = 200;
    @InjectMocks
    private AlgorithmGolWithSectors algorithmGol;

    @ParameterizedTest
    @Disabled("Too expensive for normal test run")
    @ValueSource(ints = {200, 300, 900, 1000, 1100, 2000})
    void profileRandom(int blockSize) {
        final int sectorSize = 50;
        ReflectionTestUtils.setField(algorithmGol, "blockSize", blockSize);
        int totalSectors = blockSize / sectorSize;
        int sectorFactor = blockSize / totalSectors;

        ReflectionTestUtils.setField(algorithmGol, "sectorSize", sectorSize);
        ReflectionTestUtils.setField(algorithmGol, "totalSectors", totalSectors);
        ReflectionTestUtils.setField(algorithmGol, "sectorFactor", sectorFactor);

        Block block = null;
        for (int i = 0; i < CELL_UPDATES / ((long) blockSize * blockSize); i++) {
            if (i % MAX_BLOCK_AGE == 0) {
                block = new Block(0, 0, UUID.randomUUID(), blockSize);
                setBlockToRandom(block, 6, blockSize);
            }
            algorithmGol.setNextState(block);
        }
    }

    @ParameterizedTest
    @Disabled("Too expensive for normal test run")
    @ValueSource(ints = {5, 10, 20, 50, 100, 200})
    void profileSector(int sectorSize) {
        final int blockSize = 2000;
        ReflectionTestUtils.setField(algorithmGol, "blockSize", blockSize);
        int totalSectors = blockSize / sectorSize;
        int sectorFactor = blockSize / totalSectors;

        ReflectionTestUtils.setField(algorithmGol, "sectorSize", sectorSize);
        ReflectionTestUtils.setField(algorithmGol, "totalSectors", totalSectors);
        ReflectionTestUtils.setField(algorithmGol, "sectorFactor", sectorFactor);

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