package slobben.cells.service.workers;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import slobben.cells.entities.Block;
import slobben.cells.enums.BlockState;
import slobben.cells.service.ExecutorService;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AlgorithmGolWithSectors implements Worker {

    private final ExecutorService executorService;
    private final Map<String, Block> blocks;

    @Value("${cells.size.blockSize}")
    private int blockSize = 500;
    @Value("${cells.size.sectorSize}")
    private int sectorSize = 100;
    private int totalSectors = 0;
    private int sectorFactor = 0;

    private static void applyRule(boolean[][] cells, byte[][] heatmap, short[][] sectorCounts, int x, int y, int sx, int sy) {
        byte heat = heatmap[x][y];
        if (!cells[x][y]) {
            if (heat == 3) {
                cells[x][y] = true;
                sectorCounts[sx][sy]++;
            }
        } else {
            if (!(heat == 3 || heat == 2)) {
                cells[x][y] = false;
                sectorCounts[sx][sy]--;
            }
        }
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

    @PostConstruct
    void init() {
        if (blockSize % sectorSize != 0) {
            throw new IllegalStateException();
        }
        totalSectors = blockSize / sectorSize;
        sectorFactor = blockSize / totalSectors;
    }

    @Override
    public String getName() {
        return "GOL Algorithm";
    }

    @Override
    public void execute() {
        Set<Runnable> tasks = blocks.values().stream().map(block -> ((Runnable) () -> setNextState(block))).collect(Collectors.toSet());
        executorService.executeTasksParallel(tasks, getName());
    }

    public void setNextState(Block block) {
        if (block.getBlockState() == BlockState.HIBERNATION) {
            block.setNextHibernationState();
            return;
        }
        if (block.getSectorCounts() == null) {
            setInitialSectorHeat(block);
        }

        byte[][] heatmap = getNewHeatmap(block.getCells(), block.getSectorCounts());
        applyGameOfLifeRulesFromHeatmap(block.getCells(), heatmap, block.getSectorCounts());

        block.blockUpdated();
    }

    private void setInitialSectorHeat(Block block) {
        block.setSectorCounts(new short[totalSectors][totalSectors]);
        boolean[][] matrix = block.getCells();
        short[][] sector = block.getSectorCounts();
        for (int x = 1; x <= blockSize; x++) {
            for (int y = 1; y <= blockSize; y++) {
                if (!matrix[x][y]) continue;
                int sectorX = Math.floorDiv(x - 1, sectorFactor);
                int sectorY = Math.floorDiv(y - 1, sectorFactor);
                sector[sectorX][sectorY]++;
            }
        }
    }

    private byte[][] getNewHeatmap(boolean[][] matrix, short[][] sectorCounts) {
        int blockSizeWithBorder = blockSize + 2;
        byte[][] heatmap = new byte[blockSizeWithBorder][blockSizeWithBorder];

        // border loops. Only those need the expensive out-of-index guards
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

        // With border cells out of the way we can zoom on the block cells
        for (int sx = 0; sx < totalSectors; sx++) {
            for (int sy = 0; sy < totalSectors; sy++) {
                if (sectorCounts[sx][sy] > 0) {
                    final int startX = sx * sectorSize + 1;
                    final int startY = sy * sectorSize + 1;
                    final int endX = startX + sectorSize;
                    final int endY = startY + sectorSize;

                    for (int x = startX; x < endX; x++) {
                        for (int y = startY; y < endY; y++) {

                            // skip when the current cell is dead
                            if (!matrix[x][y]) continue;

                            // loop over all the neighbors to increment neighbor count
                            heatmap[x - 1][y - 1]++;
                            heatmap[x - 1][y]++;
                            heatmap[x - 1][y + 1]++;

                            heatmap[x][y - 1]++;
                            heatmap[x][y + 1]++;

                            heatmap[x + 1][y - 1]++;
                            heatmap[x + 1][y]++;
                            heatmap[x + 1][y + 1]++;
                        }
                    }
                }
            }
        }
        return heatmap;
    }

    private void applyGameOfLifeRulesFromHeatmap(boolean[][] cells, byte[][] heatmap, short[][] sectorCounts) {
        for (int sx = 0; sx < totalSectors; sx++) {
            for (int sy = 0; sy < totalSectors; sy++) {
                final int startX = sx * sectorSize + 1;
                final int startY = sy * sectorSize + 1;
                final int endX = startX + sectorSize;
                final int endY = startY + sectorSize;

                if (sectorCounts[sx][sy] == 0) {
                    for (int x = startX; x < endX; x++) {
                        applyRule(cells, heatmap, sectorCounts, x, startY, sx, sy);
                        applyRule(cells, heatmap, sectorCounts, x, endY, sx, sy);
                    }
                    for (int y = startY + 1; y < endY - 1; y++) {
                        applyRule(cells, heatmap, sectorCounts, startX, y, sx, sy);
                        applyRule(cells, heatmap, sectorCounts, endX, y, sx, sy);
                    }
                } else {
                    for (int x = startX; x < endX; x++) {
                        for (int y = startY; y < endY; y++) {
                            applyRule(cells, heatmap, sectorCounts, x, y, sx, sy);
                        }
                    }
                }
            }
        }
    }

}