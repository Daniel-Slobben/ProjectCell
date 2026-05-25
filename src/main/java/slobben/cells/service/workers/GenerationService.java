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
    private int blockSize;

    @Override
    public String getName() {
        return "Generation";
    }

    @Override
    public void execute() {
        Set<Runnable> tasks = blocks.values().stream().map(block -> ((Runnable) () -> setNextState(block))).collect(Collectors.toSet());
        executorService.executeTasksParallel(tasks);
    }

    public void setNextState(Block block) {
        if (block.getBlockState() == BlockState.HIBERNATION) {
            block.setNextHibernationState();
            return;
        }

        byte[][] heatmap = getNeighboursHeatmap(block.getCells());
        applyGameOfLifeRulesFromHeatmap(block, heatmap);
    }

    private void applyGameOfLifeRulesFromHeatmap(Block block, byte[][] heatmap) {
        // loop inner matrix (no border cells)
        for (int x = 1; x < blockSize + 1; x++) {
            for (int y = 1; y < blockSize + 1; y++) {
                // If cell was dead
                if (!block.getCells()[x][y]) {
                    if (heatmap[x][y] == 3) {
                        block.getCells()[x][y] = true;
                    }
                }
                // If cell was alive
                else {
                    if (!(heatmap[x][y] == 2 || heatmap[x][y] == 3)) {
                        block.getCells()[x][y] = false;
                    }
                }
            }
        }
    }

    private byte[][] getNeighboursHeatmap(boolean[][] matrix) {
        int blockSizeWithBorder = environmentConfig.getBlockSizeWithBorder();
        byte[][] heatmap = new byte[blockSizeWithBorder][blockSizeWithBorder];

        // loop over every cell in the matrix
        for (int x = 0; x < blockSizeWithBorder; x++) {
            for (int y = 0; y < blockSizeWithBorder; y++) {

                // skip when the current cell is dead
                if (!matrix[x][y]) continue;

                // loop over all the neighbors to increment neighbor count
                for (int i = -1; i <= 1; i++) {
                    for (int j = -1; j <= 1; j++) {

                        // skip itself
                        if (i == 0 && j == 0) continue;

                        // skip out of index cells
                        if (x + i < 0 || y + j < 0) continue;
                        if (x + i >= blockSizeWithBorder || y + j >= blockSizeWithBorder) continue;

                        heatmap[x + i][y + j]++;
                    }
                }
            }
        }
        return heatmap;
    }
}
