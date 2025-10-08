package slobben.Cells;

import lombok.Cleanup;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import slobben.Cells.entities.model.Block;
import slobben.Cells.enums.Direction;
import slobben.Cells.service.InitializerService;
import slobben.Cells.service.StitchingService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@ComponentScan("slobben.Cells.service")
@ActiveProfiles(profiles = "unit")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class StitchingTest {
    private final StitchingService stitchingService;
    private final InitializerService initializerService;

    @Autowired
    public StitchingTest(StitchingService stitchingService, InitializerService initializerService) {
        this.stitchingService = stitchingService;
        this.initializerService = initializerService;
    }

    @ParameterizedTest
    @EnumSource(value = Direction.class)
    public void testStitching(Direction direction) {
        List<Block> blocks = initializerService.initializeMap();
        assert blocks.size() == 1;
        var block = blocks.getFirst();
        var cells = block.getCells();
        //corner cells
        cells[1][1] = true;
        cells[1][10] = true;
        cells[10][1] = true;
        cells[10][10] = true;

        stitchingService.initializeStitch(block);
        List<Block> newBlocks = stitchingService.addBorderCells(block);
        assert newBlocks.size() == 8;
        newBlocks.forEach(stitchingService::stitchBlock);

        var blockToCheck = newBlocks.stream().filter(b -> b.getX() == direction.getDx()) .filter(b -> b.getY() == direction.getDy()).findFirst().get();
        blocks.addAll(newBlocks);
        //blocks.forEach(stitchingService::stitchBlock);

        switch(direction) {
            case Direction.TOP_LEFT -> assertThat(blockToCheck.getCells()[11][11]).isTrue();
            case Direction.TOP -> {
                assertThat(blockToCheck.getCells()[11][10]).isTrue();
                assertThat(blockToCheck.getCells()[11][1]).isTrue();
            }
            case Direction.TOP_RIGHT-> assertThat(blockToCheck.getCells()[11][0]).isTrue();
            case Direction.LEFT -> {
                assertThat(blockToCheck.getCells()[1][11]).isTrue();
                assertThat(blockToCheck.getCells()[10][11]).isTrue();
            }
            case Direction.RIGHT -> {
                assertThat(blockToCheck.getCells()[1][0]).isTrue();
                assertThat(blockToCheck.getCells()[10][0]).isTrue();
            }
            case Direction.BOTTOM_LEFT-> assertThat(blockToCheck.getCells()[0][11]).isTrue();
            case Direction.BOTTOM -> {
                assertThat(blockToCheck.getCells()[0][10]).isTrue();
                assertThat(blockToCheck.getCells()[0][1]).isTrue();
            }
            case Direction.BOTTOM_RIGHT -> assertThat(blockToCheck.getCells()[0][0]).isTrue();
        }
    }

}
