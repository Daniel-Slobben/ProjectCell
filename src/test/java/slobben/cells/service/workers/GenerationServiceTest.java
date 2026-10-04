package slobben.cells.service.workers;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import slobben.cells.config.EnvironmentConfig;
import slobben.cells.entities.model.Block;

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

    @BeforeEach
    void init() {
        ReflectionTestUtils.setField(generationService, "blockSize", 500);
    }

    @Test
    void profileRandom() {
        Block block = new Block(0, 0, UUID.randomUUID(), 500);
        setBlockToRandom(block, 6, 500);

        for (int i = 0; i < 10000; i++) {
            generationService.setNextState(block);
        }
    }

    @Test
    void profileLine() {
        Block block = new Block(0, 0, UUID.randomUUID(), 500);
        setLine(block, 500);

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