package slobben.cells.service;

import slobben.cells.entities.model.Block;
import slobben.cells.enums.CellState;

import static slobben.cells.enums.CellState.ALIVE;
import static slobben.cells.enums.CellState.DEAD;

public class GenerationService {

    @SuppressWarnings({"java:S3776", "java:S135"})
    public static void setNextState(Block block) {
        final int blockSizeWithBorder = block.getCells().length;
        final int blockSize = blockSizeWithBorder - 2;

        byte[][] heatmap = new byte[blockSizeWithBorder][blockSizeWithBorder];
        for (int x = 0; x < blockSizeWithBorder; x++) {
            for (int y = 0; y < blockSizeWithBorder; y++) {
                if (!block.getCells()[x][y]) continue;
                for (int i = -1; i <= 1; i++) {
                    for (int j = -1; j <= 1; j++) {
                        if (i == 0 && j == 0) continue;
                        if (x + i < 0 || y + j < 0 || x + i >= blockSizeWithBorder || y + j >= blockSizeWithBorder) continue;
                        heatmap[x + i][y + j]++;
                    }
                }
            }
        }

        for (int x = 1; x < blockSize + 1; x++) {
            for (int y = 1; y < blockSize + 1; y++) {
                // If cell was dead
                if (!block.getCells()[x][y]) {
                    if (applyConwayGameOfLifeRules(DEAD, heatmap[x][y]).equals(ALIVE)) {
                        block.getCells()[x][y] = true;
                    }
                }
                // If cell was alive
                else {
                    if (applyConwayGameOfLifeRules(ALIVE, heatmap[x][y]).equals(DEAD)) {
                        block.getCells()[x][y] = false;
                    }
                }
            }
        }
    }

    private static CellState applyConwayGameOfLifeRules(CellState cellState, int aliveCounter) {
        if (cellState == ALIVE) {
            return (aliveCounter == 2 || aliveCounter == 3) ? ALIVE : DEAD;
        } else {
            return (aliveCounter == 3) ? ALIVE : DEAD;
        }
    }
}
