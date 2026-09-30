package slobben.cells.service.workers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ActiveProfiles;
import slobben.cells.config.EnvironmentConfig;
import slobben.cells.entities.model.Block;
import slobben.cells.util.BlockUtils;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ComponentScan("slobben.cells.service")
@ActiveProfiles("unit")
class BigBlockServiceTest {

    @Autowired
    private BigBlockService bigBlockService;
    @Autowired
    private Map<String, Block> blocks;
    @Autowired
    private Map<String, Block> bigBlocks;
    @Autowired
    private EnvironmentConfig environmentConfig;

    @BeforeEach
    void clearBlocks() {
        this.blocks.clear();
        this.bigBlocks.clear();
    }


    @Test
    void execute() {
        // prepare
        Block block = new Block(0, 0, UUID.randomUUID(), environmentConfig.getBlockSize());
        block.getCells()[1][1] = true;
        block.getCells()[1 + environmentConfig.getBigBlockFactor()][1 + environmentConfig.getBigBlockFactor()] = true;
        this.blocks.put(block.getKey(), block);

        Block block2 = new Block(1, 1, UUID.randomUUID(), environmentConfig.getBlockSize());
        block2.getCells()[10][10] = true;
        this.blocks.put(block2.getKey(), block2);

        // execute
        bigBlockService.execute();

        // verify
        assertThat(this.bigBlocks).hasSize(1);
        assertThat(this.bigBlocks.get(BlockUtils.getKey(0, 0)).getCells()[1][1]).isTrue();
        assertThat(this.bigBlocks.get(BlockUtils.getKey(0, 0)).getCells()[2][2]).isTrue();
        assertThat(this.bigBlocks.get(BlockUtils.getKey(0, 0)).getCells()[10][10]).isTrue();
    }
}