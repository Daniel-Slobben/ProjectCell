package slobben.cells;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ActiveProfiles;
import slobben.cells.entities.model.Block;
import slobben.cells.service.workers.GenerationService;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ComponentScan("slobben.cells.service")
@ActiveProfiles(profiles = "unit")
class GenerationServiceTests {

    @Autowired
    private GenerationService generationService;
    @Autowired
    private Map<String, Block> blocks;

    @Test
    void checkTick() {
        Block block = blocks.values().stream().filter(b -> b.getX() == 0).filter(b -> b.getY() == 0).findFirst().get();
        block.getCells()[0][0] = true;
        block.getCells()[0][1] = true;
        block.getCells()[1][0] = true;
        block.getCells()[1][1] = true;
        generationService.setNextState(block);

        assertThat(block.getCells()[0][0]).isTrue();
        assertThat(block.getCells()[0][1]).isTrue();
        assertThat(block.getCells()[1][0]).isTrue();
        assertThat(block.getCells()[1][1]).isTrue();
    }
}