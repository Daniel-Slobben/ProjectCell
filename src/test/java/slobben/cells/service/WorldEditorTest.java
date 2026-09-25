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
import slobben.cells.dto.internal.BlockUpdate;
import slobben.cells.util.BlockUtils;

import java.util.Map;
import java.util.UUID;

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
        UUID id = UUID.randomUUID();

        // execute
        worldEditor.setCell(0, 0, id);
        worldEditor.setCell(0, 1, id);
        worldEditor.setCell(1, 0, id);
        worldEditor.setCell(1, 1, id);

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
        UUID id = UUID.randomUUID();

        // execute
        int blockSize = environmentConfig.getBlockSize();
        // execute
        worldEditor.setCell(blockSize - 1, blockSize - 1, id);
        worldEditor.setCell(blockSize - 1, blockSize, id);
        worldEditor.setCell(blockSize, blockSize - 1, id);
        worldEditor.setCell(blockSize, blockSize, id);

        // verify
        assertThat(blockUpdates).hasSize(4);
        assertThat(blockUpdates.get(BlockUtils.getKey(0, 0)).state()[blockSize - 1][blockSize - 1]).isTrue();
        assertThat(blockUpdates.get(BlockUtils.getKey(0, 1)).state()[blockSize - 1][0]).isTrue();
        assertThat(blockUpdates.get(BlockUtils.getKey(1, 0)).state()[0][blockSize - 1]).isTrue();
        assertThat(blockUpdates.get(BlockUtils.getKey(1, 1)).state()[0][0]).isTrue();
    }
}
