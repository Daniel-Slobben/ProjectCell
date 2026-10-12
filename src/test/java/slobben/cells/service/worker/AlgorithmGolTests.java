package slobben.cells.service.worker;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ActiveProfiles;
import slobben.cells.entities.Block;
import slobben.cells.service.workers.AlgorithmGol;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ComponentScan("slobben.cells.service")
@ActiveProfiles(profiles = "unit")
class AlgorithmGolTests {

    @Autowired
    private AlgorithmGol algorithmGol;
    @Autowired
    private Map<String, Block> blocks;
    @Value("${cells.size.blockSize}")
    private int blockSize = 500;

    @Test
    void checkTick() {
        Block block = new Block(0, 0, UUID.randomUUID(), blockSize);
        blocks.put(block.getKey(), block);
        block.getCells()[0][0] = true;
        block.getCells()[0][1] = true;
        block.getCells()[1][0] = true;
        block.getCells()[1][1] = true;
        block.getCells()[2][2] = true;
        block.getCells()[1][2] = true;
        block.getCells()[2][1] = true;
        algorithmGol.setNextState(block);

        assertThat(block.getCells()[0][0]).isTrue();
        assertThat(block.getCells()[0][1]).isTrue();
        assertThat(block.getCells()[1][0]).isTrue();
        assertThat(block.getCells()[1][1]).isTrue();
    }

}