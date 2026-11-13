package slobben.cells.service.worker;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import slobben.cells.entities.Block;
import slobben.cells.enums.Direction;
import slobben.cells.service.workers.AddNewBlocks;
import slobben.cells.service.workers.Stitching;
import slobben.cells.util.BlockUtils;
import slobben.cells.util.PackedRows;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ComponentScan({"slobben.cells.service", "slobben.cells.config"})
@ActiveProfiles(profiles = "unit")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class StitchingTest {

    @Autowired
    private Stitching stitching;
    @Autowired
    private Map<String, Block> newBlocks;
    @Autowired
    private AddNewBlocks addNewBlocks;
    @Autowired
    private Map<String, Block> blocks;

    @Value("${cells.size.blockSize}")
    private int blockSize = 10;

    @ParameterizedTest
    @EnumSource(value = Direction.class)
    void bordersReachTheNeighbours(Direction direction) {
        // prepare
        Block block = new Block(0, 0, UUID.randomUUID(), blockSize);
        blocks.put(block.getKey(), block);
        newBlocks.clear();

        var cells = block.getCells();
        cells[1][1] = true;
        cells[1][10] = true;
        cells[10][1] = true;
        cells[10][10] = true;
        cells[1][2] = true;

        // execute
        stitching.execute();
        addNewBlocks.execute();

        // verify
        assertThat(blocks).hasSize(9);
        Block blockToCheck = blocks.get(BlockUtils.getKey(direction.getDx(), direction.getDy()));

        switch (direction) {
            case Direction.LOW_X_LOW_Y -> assertThat(isAlive(blockToCheck, 11, 11)).isTrue();
            case Direction.LOW_X_MID_Y -> {
                assertThat(isAlive(blockToCheck, 11, 10)).isTrue();
                assertThat(isAlive(blockToCheck, 11, 1)).isTrue();
                assertThat(isAlive(blockToCheck, 11, 2)).isTrue();
            }
            case Direction.LOW_X_HIGH_Y -> assertThat(isAlive(blockToCheck, 11, 0)).isTrue();
            case Direction.MID_X_LOW_Y -> {
                assertThat(isAlive(blockToCheck, 1, 11)).isTrue();
                assertThat(isAlive(blockToCheck, 10, 11)).isTrue();
            }
            case Direction.MID_X_HIGH_Y -> {
                assertThat(isAlive(blockToCheck, 1, 0)).isTrue();
                assertThat(isAlive(blockToCheck, 10, 0)).isTrue();
            }
            case Direction.HIGH_X_LOW_Y -> assertThat(isAlive(blockToCheck, 0, 11)).isTrue();
            case Direction.HIGH_X_MID_Y -> {
                assertThat(isAlive(blockToCheck, 0, 10)).isTrue();
                assertThat(isAlive(blockToCheck, 0, 1)).isTrue();
            }
            case Direction.HIGH_X_HIGH_Y -> assertThat(isAlive(blockToCheck, 0, 0)).isTrue();
        }
    }

    // Stitching writes the borders into the packed rows
    private static boolean isAlive(Block block, int x, int y) {
        return PackedRows.get(block.getPackedCells()[x], y);
    }
}
