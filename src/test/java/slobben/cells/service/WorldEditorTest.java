package slobben.cells.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import slobben.cells.config.EnvironmentConfig;
import slobben.cells.entities.Pattern;
import slobben.cells.entities.model.Block;
import slobben.cells.util.BlockUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@ComponentScan("slobben.cells.service")
@ActiveProfiles(profiles = "normal")
class WorldEditorTest {

    @Autowired
    private WorldEditor worldEditor;
    @Autowired
    private Map<String, Block> blocks;
    @Autowired
    private EnvironmentConfig environmentConfig;

    @BeforeEach
    void clearBlocks() {
        this.blocks.clear();
    }

    @Test
    void singleBlock() {
        // prepare
        boolean[][] matrix = new boolean[4][4];
        matrix[0][0] = true;
        matrix[0][1] = true;
        matrix[1][0] = true;
        matrix[1][1] = true;

        Pattern pattern = new Pattern("test", 4, 4, matrix);
        assertThat(blocks).isEmpty();

        // execute
        worldEditor.setCells(0, 0, pattern);

        // verify
        Block block = blocks.get(BlockUtils.getKey(0, 0));
        assertThat(block).isNotNull();

        boolean[][] cells = block.getCells();

        // remember to adjust for bordercells in Block.cells
        assertThat(cells[1][1]).isTrue();
        assertThat(cells[1][2]).isTrue();
        assertThat(cells[2][1]).isTrue();
        assertThat(cells[2][2]).isTrue();
        assertThat(cells[2][3]).isFalse();
    }

    @Test
    void multipleBlocks() {
        // prepare
        boolean[][] matrix = new boolean[4][4];
        matrix[0][0] = true;
        matrix[0][1] = true;
        matrix[1][0] = true;
        matrix[1][1] = true;

        Pattern pattern = new Pattern("test", 4, 4, matrix);
        assertThat(blocks).isEmpty();

        // execute
        int blockSize = environmentConfig.getBlockSize();
        worldEditor.setCells(blockSize - 1, blockSize - 1, pattern);

        // verify
        assertThat(blocks).hasSize(4);
        assertThat(blocks.get(BlockUtils.getKey(0, 0)).getCells()[blockSize][blockSize]).isTrue();
        assertThat(blocks.get(BlockUtils.getKey(0, 1)).getCells()[blockSize][1]).isTrue();
        assertThat(blocks.get(BlockUtils.getKey(1, 0)).getCells()[1][blockSize]).isTrue();
        assertThat(blocks.get(BlockUtils.getKey(1, 1)).getCells()[1][1]).isTrue();
    }

    @Test
    void bigPattern() {
        // prepare
        boolean[][] matrix = new boolean[1500][200];
        matrix[0][0] = true;
        matrix[0][1] = true;
        matrix[1][0] = true;
        matrix[1][1] = true;
        matrix[1499][199] = true;

        Pattern pattern = new Pattern("test", 1500, 200, matrix);
        assertThat(blocks).isEmpty();

        // execute
        worldEditor.setCells(-600, -600, pattern);

        // verify
        int blockSize = environmentConfig.getBlockSize();
        assertThat(blocks).hasSize(8);
        assertThat(blocks.get(BlockUtils.getKey(-2, -2)).getCells()[blockSize - 99][blockSize - 99]).isTrue();
    }
}
