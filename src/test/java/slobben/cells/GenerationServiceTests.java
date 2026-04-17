package slobben.cells;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import slobben.cells.entities.model.Block;
import slobben.cells.service.workers.GenerationService;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@ComponentScan("slobben.cells.service")
@ActiveProfiles(profiles = "unit")
class GenerationServiceTests {

    @Autowired
    private GenerationService generationService;
    @Autowired
    private Set<Block> blocks;

    @Test
    void checkTick() {
        Block block = blocks.stream().filter(b -> b.getX() == 0).filter(b -> b.getY() == 0).findFirst().get();
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