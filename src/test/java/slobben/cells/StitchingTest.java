package slobben.cells;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import slobben.cells.dto.BlockUpdate;
import slobben.cells.entities.model.Block;
import slobben.cells.enums.Direction;
import slobben.cells.service.workers.BorderService;
import slobben.cells.service.workers.NewBlockService;
import slobben.cells.service.workers.StitchingService;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@ComponentScan({"slobben.cells.service", "slobben.cells.config"})
@ActiveProfiles(profiles = "unit")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class StitchingTest {

    @Autowired
    private BorderService borderService;
    @Autowired
    private List<BlockUpdate> blockUpdates;
    @Autowired
    private NewBlockService newBlockService;
    @Autowired
    private StitchingService stitchingService;
    @Autowired
    private Set<Block> blocks;

    @ParameterizedTest
    @EnumSource(value = Direction.class)
    void testStitching(Direction direction) {
        assert blocks.size() == 1;
        var block = blocks.stream().findFirst().get();
        var cells = block.getCells();
        //corner cells
        cells[1][1] = true;
        cells[1][10] = true;
        cells[10][1] = true;
        cells[10][10] = true;


        borderService.addBorderCells(block);
        assert blockUpdates.size() == 8;
        newBlockService.tic();
        assert blocks.size() == 9;

        blocks.forEach(stitchingService::stitchBlock);

        Block blockToCheck = blocks.stream().filter(b -> b.getX() == direction.getDx()).filter(b -> b.getY() == direction.getDy()).findFirst().get();

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
