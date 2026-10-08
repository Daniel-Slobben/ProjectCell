package slobben.cells.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ActiveProfiles;
import slobben.cells.config.EnvironmentConfig;
import slobben.cells.entities.Block;
import slobben.cells.util.BlockUtils;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ComponentScan("slobben.cells.service")
@ActiveProfiles(profiles = "normal")
class WorldEditorTest {

    @Autowired
    private WorldEditor worldEditor;
    @Autowired
    private Map<String, Block> newBlocks;
    @Autowired
    private EnvironmentConfig environmentConfig;

    @BeforeEach
    void clearBlocks() {
        this.newBlocks.clear();
    }

    @Test
    void singleBlock() {
        // prepare
        UUID id = UUID.randomUUID();

        // execute
        worldEditor.setCell(0, 0, id);
        worldEditor.setCell(0, 1, id);
        worldEditor.setCell(1, 0, id);
        worldEditor.setCell(1, 1, id);

        // verify
        Block block = newBlocks.get(BlockUtils.getKey(0, 0));
        assertThat(block).isNotNull();

        boolean[][] cells = block.getCells();

        // remember to adjust for bordercells here
        assertThat(cells[1][1]).isTrue();
        assertThat(cells[1][2]).isTrue();
        assertThat(cells[2][1]).isTrue();
        assertThat(cells[2][2]).isTrue();
        assertThat(cells[2][3]).isFalse();
    }

    @Test
    void multipleBlocks() {
        // prepare
        UUID id = UUID.randomUUID();

        // execute
        int blockSize = environmentConfig.getBlockSize();
        // execute
        worldEditor.setCell(blockSize - 1, blockSize - 1, id);
        worldEditor.setCell(blockSize - 1, blockSize, id);
        worldEditor.setCell(blockSize, blockSize - 1, id);
        worldEditor.setCell(blockSize, blockSize, id);

        // verify
        assertThat(newBlocks).hasSize(4);
        assertThat(newBlocks.get(BlockUtils.getKey(0, 0)).getCells()[blockSize][blockSize]).isTrue();
        assertThat(newBlocks.get(BlockUtils.getKey(0, 1)).getCells()[blockSize][1]).isTrue();
        assertThat(newBlocks.get(BlockUtils.getKey(1, 0)).getCells()[1][blockSize]).isTrue();
        assertThat(newBlocks.get(BlockUtils.getKey(1, 1)).getCells()[1][1]).isTrue();
    }
}
