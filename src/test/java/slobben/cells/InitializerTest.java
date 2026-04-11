package slobben.cells;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import slobben.cells.config.BlockConfig;
import slobben.cells.entities.model.Block;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class InitializerTest {

    @Test
    void getEmptyMap() {
        // prepare
        int blockSize = 200;
        int blockAmount = 30;
        int blockSizeWithBorder = blockSize + 2;

        BlockConfig.setBlockSize(blockSize);
        BlockConfig.setBlockAmount(blockAmount);
        BlockConfig.setBlockSizeWithBorder(blockSizeWithBorder);

        boolean[][] expectedDimensions = new boolean[blockSizeWithBorder][blockSizeWithBorder];

        // execute
        Set<Block> blocks = BlockConfig.getEmptyMap();

        // verify
        Assertions.assertNotNull(blocks);

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
        int blockSize = 10;
        int blockAmount = 10;
        int blockPopulation = 2;
        int cellPopulation = 2;

        BlockConfig.setBlockSize(blockSize);
        BlockConfig.setBlockAmount(blockAmount);
        BlockConfig.setBlockSizeWithBorder(blockSize + 2);
        BlockConfig.setBlockPopulation(blockPopulation);
        BlockConfig.setCellPopulation(cellPopulation);

        // execute
        Set<Block> blocks = BlockConfig.getRandomMap();

        // verify
        Assertions.assertNotNull(blocks);

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
        assertThat(counter).isCloseTo((blockSize * blockSize) / blockPopulation, Offset.offset(10));
    }
}
