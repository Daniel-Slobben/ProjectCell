package slobben.cells.service.workers;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import slobben.cells.config.EnvironmentConfig;
import slobben.cells.entities.model.Block;
import slobben.cells.enums.BlockState;
import slobben.cells.service.ExecutorService;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GenerationService implements Worker {

    private final EnvironmentConfig environmentConfig;
    private final ExecutorService executorService;
    private final Map<String, Block> blocks;

    @Value("${cells.size.blockSize}")
    private int blockSize = 500;

    @Override
    public String getName() {
        return "Generation";
    }

    @Override
    public void execute() {
        Set<Runnable> tasks = blocks.values().stream().map(block -> ((Runnable) () -> setNextState(block))).collect(Collectors.toSet());
        executorService.executeTasksParallel(tasks, getName());
    }

    private static void neighborLoopWithIndexCheck(int x, int y, int blockSizeWithBorder, byte[][] heatmap) {
        for (int i = -1; i <= 1; i++) {
            if (x + i < 0 || x + i >= blockSizeWithBorder) continue;
            for (int j = -1; j <= 1; j++) {
                if (y + j < 0 || y + j >= blockSizeWithBorder) continue;

                heatmap[x + i][y + j]++;
            }
        }
    }

    public void setNextState(Block block) {
        if (block.getBlockState() == BlockState.HIBERNATION) {
            block.setNextHibernationState();
            return;
        }

        byte[][] heatmap = getNewHeatmap(block.getCells());
        applyGameOfLifeRulesFromHeatmap(block.getCells(), heatmap);

        block.blockUpdated();
    }

    private void applyGameOfLifeRulesFromHeatmap(boolean[][] cells, byte[][] heatmap) {
        for (int x = 1; x < blockSize + 1; x++) {
            for (int y = 1; y < blockSize + 1; y++) {
                byte heat = heatmap[x][y];
                if (!cells[x][y]) {
                    if (heat == 3) {
                        cells[x][y] = true;
                    }
                }
                else {
                    // 3 and 4 cause we increment itself if cell is true
                    if (!(heat == 4 || heat == 3)) {
                        cells[x][y] = false;
                    }
                }
            }
        }
    }

    private byte[][] getNewHeatmap(boolean[][] matrix) {
        int blockSizeWithBorder = blockSize + 2;
        byte[][] heatmap = new byte[blockSizeWithBorder][blockSizeWithBorder];

        // border loops
        for (int x = 0; x < blockSizeWithBorder; x++) {
            if (matrix[x][0]) {
                neighborLoopWithIndexCheck(x, 0, blockSizeWithBorder, heatmap);
            }
            if (matrix[x][blockSizeWithBorder - 1]) {
                neighborLoopWithIndexCheck(x, blockSizeWithBorder - 1, blockSizeWithBorder, heatmap);
            }
        }
        for (int y = 1; y < blockSizeWithBorder - 1; y++) {
            if (matrix[0][y]) {
                neighborLoopWithIndexCheck(0, y, blockSizeWithBorder, heatmap);
            }
            if (matrix[blockSizeWithBorder - 1][y]) {
                neighborLoopWithIndexCheck(blockSizeWithBorder - 1, y, blockSizeWithBorder, heatmap);
            }
        }

        // inner loop
        for (int x = 1; x < blockSizeWithBorder - 1; x++) {
            for (int y = 1; y < blockSizeWithBorder - 1; y++) {

                // skip when the current cell is dead
                if (!matrix[x][y]) continue;

                // loop over all the neighbors to increment neighbor count
                for (int i = -1; i <= 1; i++) {
                    for (int j = -1; j <= 1; j++) {
                        heatmap[x + i][y + j]++;
                    }
                }
            }
        }
        return heatmap;
    }
}