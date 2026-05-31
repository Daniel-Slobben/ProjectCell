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
import slobben.cells.dto.BlockUpdate;
import slobben.cells.entities.Pattern;
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
    private Map<String, BlockUpdate> blockUpdates;
    @Autowired
    private EnvironmentConfig environmentConfig;

    @BeforeEach
    void clearBlocks() {
        this.blockUpdates.clear();
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
        assertThat(blockUpdates).isEmpty();

        // execute
        worldEditor.setCells(0, 0, pattern);

        // verify
        BlockUpdate block = blockUpdates.get(BlockUtils.getKey(0, 0));
        assertThat(block).isNotNull();

        boolean[][] cells = block.state();

        // remember to adjust for bordercells in Block.cells
        assertThat(cells[0][0]).isTrue();
        assertThat(cells[0][1]).isTrue();
        assertThat(cells[1][0]).isTrue();
        assertThat(cells[1][1]).isTrue();
        assertThat(cells[1][2]).isFalse();
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
        assertThat(blockUpdates).isEmpty();

        // execute
        int blockSize = environmentConfig.getBlockSize();
        worldEditor.setCells(blockSize - 1, blockSize - 1, pattern);

        // verify
        assertThat(blockUpdates).hasSize(4);
        assertThat(blockUpdates.get(BlockUtils.getKey(0, 0)).state()[blockSize - 1][blockSize - 1]).isTrue();
        assertThat(blockUpdates.get(BlockUtils.getKey(0, 1)).state()[blockSize - 1][0]).isTrue();
        assertThat(blockUpdates.get(BlockUtils.getKey(1, 0)).state()[0][blockSize - 1]).isTrue();
        assertThat(blockUpdates.get(BlockUtils.getKey(1, 1)).state()[0][0]).isTrue();
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
        assertThat(blockUpdates).isEmpty();

        // execute
        worldEditor.setCells(-600, -600, pattern);

        // verify
        int blockSize = environmentConfig.getBlockSize();
        assertThat(blockUpdates).hasSize(8);
        assertThat(blockUpdates.get(BlockUtils.getKey(-2, -2)).state()[blockSize - 100][blockSize - 100]).isTrue();
    }
}
