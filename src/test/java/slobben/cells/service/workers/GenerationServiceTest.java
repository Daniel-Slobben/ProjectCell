package slobben.cells.service.workers;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import slobben.cells.config.EnvironmentConfig;
import slobben.cells.entities.Block;

import java.util.Random;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
@RequiredArgsConstructor
class GenerationServiceTest {

    private static final Random random = new Random();
    @InjectMocks
    private GenerationService generationService;
    @Mock
    private EnvironmentConfig environmentConfig;

    private static final long CELL_UPDATES = 2_000_000_000;
    private static final int MAX_BLOCK_AGE = 200;

    //    @ParameterizedTest
//    @ValueSource(ints = {20, 50, 100, 200, 300, 500, 600, 700, 800, 900, 1000, 1100, 1200, 1300, 1500, 2000, 2100, 2200, 2300})
    void profileRandom(int blockSize) {
        ReflectionTestUtils.setField(generationService, "blockSize", blockSize);
        Block block = null;
        for (int i = 0; i < CELL_UPDATES / ((long) blockSize * blockSize); i++) {
            if (i % MAX_BLOCK_AGE == 0) {
                block = new Block(0, 0, UUID.randomUUID(), blockSize);
                setBlockToRandom(block, 6, blockSize);
            }
            generationService.setNextState(block);
        }
    }

    @Test
    void profileLine() {
        int blockSize = 500;
        ReflectionTestUtils.setField(generationService, "blockSize", blockSize);

        Block block = new Block(0, 0, UUID.randomUUID(), blockSize);
        setLine(block, blockSize);

        for (int i = 0; i < 10000; i++) {
            generationService.setNextState(block);
        }
    }


    private Block setBlockToRandom(Block block, int cellPopulation, int blockSize) {
        for (int x = 0; x < blockSize; x++) {
            for (int y = 0; y < blockSize; y++) {
                if (random.nextInt(0, cellPopulation) == 0) {
                    block.getCells()[x][y] = true;
                }
            }
        }
        return block;
    }

    private Block setLine(Block block, int blockSize) {
        for (int i = 0; i < blockSize + 2; i++) {
            block.getCells()[i][blockSize / 2] = true;
            block.getCells()[i][blockSize / 2 - 1] = true;
        }
        return block;
    }

}