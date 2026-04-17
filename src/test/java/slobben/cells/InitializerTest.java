package slobben.cells;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import slobben.cells.config.BlockConfig;
import slobben.cells.config.EnvironmentConfig;
import slobben.cells.entities.model.Block;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@ComponentScan({"slobben.cells.service", "slobben.cells.config"})
@ActiveProfiles(profiles = "unit")
class InitializerTest {

    @Autowired
    private BlockConfig blockConfig;
    @Autowired
    private EnvironmentConfig environmentConfig;

    @Test
    void getEmptyMap() {
        // prepare
        boolean[][] expectedDimensions = new boolean[environmentConfig.getBlockSizeWithBorder()][environmentConfig.getBlockSizeWithBorder()];

        // execute
        Set<Block> blocks = blockConfig.getEmptyMap();

        // verify
        Assertions.assertNotNull(blocks);

        int blockAmount = environmentConfig.getBlockAmount();

        for (int x = 0; x < blockAmount; x++) {
            for (int y = 0; y < blockAmount; y++) {
                int finalX = x;
                int finalY = y;
                var foundBlock = blocks.stream()
                        .filter(block -> block.getX() == finalX)
                        .filter(block -> block.getY() == finalY)
                        .findFirst()
                        .orElseThrow(() -> new AssertionError(String.format("Failed to find Block X: %s, Y: %s", finalX, finalY)));
                assertThat(foundBlock.getCells()).hasSameDimensionsAs(expectedDimensions);
            }
        }
    }

    @Test
    void getRandomMap() {
        // execute
        Set<Block> blocks = blockConfig.getRandomMap();

        // verify
        Assertions.assertNotNull(blocks);
        int blockAmount = environmentConfig.getBlockAmount();

        int counter = 0;
        for (int x = 0; x < blockAmount; x++) {
            for (int y = 0; y < blockAmount; y++) {
                int finalX = x;
                int finalY = y;
                var foundBlock = blocks.stream()
                        .filter(block -> block.getX() == finalX)
                        .filter(block -> block.getY() == finalY)
                        .findFirst()
                        .orElseThrow(AssertionError::new);

                OUTER_LOOP: for (boolean[] cellRow: foundBlock.getCells()) {
                    for (boolean cell: cellRow) {
                        if (cell) {
                            counter++;
                            break OUTER_LOOP;
                        }
                    }
                }
            }
        }
        int blockSize = environmentConfig.getBlockSize();
        int blockPopulation = environmentConfig.getBlockSize();
        assertThat(counter).isCloseTo((blockSize * blockSize) / blockPopulation, Offset.offset(10));
    }
}
