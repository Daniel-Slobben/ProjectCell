package slobben.Cells;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import slobben.Cells.entities.model.Block;
import slobben.Cells.service.InitializerService;
import slobben.Cells.service.StitchingService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@ComponentScan("slobben.Cells.service")
@ActiveProfiles(profiles = "unit")
public class StitchingTest {
    private final StitchingService stitchingService;
    private final InitializerService initializerService;

    @Autowired
    public StitchingTest(StitchingService stitchingService, InitializerService initializerService) {
        this.stitchingService = stitchingService;
        this.initializerService = initializerService;
    }

    @Test
    public void testStitching() {
        List<Block> blocks = initializerService.initializeMap();
        assert blocks.size() == 1;
        var block = blocks.getFirst();
        var cells = block.getCells();
        //corner cells
        cells[1][1] = true;
        cells[1][10] = true;
        cells[10][10] = true;
        cells[10][1] = true;

        // top
        cells[1][2] = true;
        // bottom
        cells[10][2] = true;
        // left
        cells[2][1] = true;
        // right
        cells[2][10] = true;

        stitchingService.initializeStitch(block);
        List<Block> newBlocks = stitchingService.addBorderCells(block);
        assert newBlocks.size() == 8;
        newBlocks.forEach(stitchingService::stitchBlock);

        var topLeftBlock = newBlocks.stream().filter(b -> b.getX() == -1) .filter(b -> b.getY() == -1).findFirst().get();
        var topRightblock = newBlocks.stream().filter(b -> b.getX() == -1) .filter(b -> b.getY() == 1).findFirst().get();
        var topBlock = newBlocks.stream().filter(b -> b.getX() == -1) .filter(b -> b.getY() == 0).findFirst().get();

        var bottomLeftBlock = newBlocks.stream().filter(b -> b.getX() == 1) .filter(b -> b.getY() == -1).findFirst().get();
        var bottomRightblock = newBlocks.stream().filter(b -> b.getX() == 1) .filter(b -> b.getY() == 1).findFirst().get();
        var bottomBlock = newBlocks.stream().filter(b -> b.getX() == 1) .filter(b -> b.getY() == 0).findFirst().get();

        var leftBlock = newBlocks.stream().filter(b -> b.getX() == 0) .filter(b -> b.getY() == -1).findFirst().get();
        var rightBlock = newBlocks.stream().filter(b -> b.getX() == 0) .filter(b -> b.getY() == 1).findFirst().get();

        assertThat(topLeftBlock.getCells()[11][11]).isTrue();
        assertThat(topRightblock.getCells()[11][0]).isTrue();
        assertThat(topBlock.getCells()[11][2]).isTrue();

        assertThat(bottomLeftBlock.getCells()[0][11]).isTrue();
        assertThat(bottomRightblock.getCells()[0][0]).isTrue();
        assertThat(bottomBlock.getCells()[0][2]).isTrue();

        assertThat(leftBlock.getCells()[2][11]).isTrue();
        assertThat(rightBlock.getCells()[11][2]).isTrue();
    }

}
