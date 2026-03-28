package slobben.cells;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import slobben.cells.entities.model.Block;
import slobben.cells.service.InitializerService;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class InitializerTest {

    @Test
    void getEmptyMap() {
        // prepare
        int blockSize = 200;
        int blockAmount = 30;
        int blockSizeWithBorder = blockSize + 2;

        InitializerService.setBlockSize(blockSize);
        InitializerService.setBlockAmount(blockAmount);
        InitializerService.setBlockSizeWithBorder(blockSizeWithBorder);

        boolean[][] expectedDimensions = new boolean[blockSizeWithBorder][blockSizeWithBorder];

        // execute
        Set<Block> blocks = InitializerService.getEmptyMap();

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

        InitializerService.setBlockSize(blockSize);
        InitializerService.setBlockAmount(blockAmount);
        InitializerService.setBlockSizeWithBorder(blockSize + 2);
        InitializerService.setBlockPopulation(blockPopulation);
        InitializerService.setCellPopulation(cellPopulation);

        // execute
        Set<Block> blocks = InitializerService.getRandomMap();

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
