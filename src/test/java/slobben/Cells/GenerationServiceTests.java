package slobben.Cells;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import slobben.Cells.entities.model.Block;
import slobben.Cells.service.GenerationService;
import slobben.Cells.service.InitializerService;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@ComponentScan("slobben.Cells.service")
@ActiveProfiles(profiles = "unit")
class GenerationServiceTests {

    private final InitializerService initializerService;
    private final GenerationService generationService;

    @Autowired
    public GenerationServiceTests(GenerationService generationService, InitializerService initializerService) {
        this.generationService = generationService;
        this.initializerService = initializerService;
    }

    @Test
    public void checkTick() {
        var blocks = initializerService.initializeMap();
        Block block = blocks.get(0).get(0);
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