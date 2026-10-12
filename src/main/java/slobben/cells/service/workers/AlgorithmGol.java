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
public class AlgorithmGol implements Worker {

    private final ExecutorService executorService;
    private final Map<String, Block> blocks;

    @Value("${cells.size.blockSize}")
    private int blockSize = 500;
    @Value("${cells.size.sectorSize}")
    private int sectorSize = 100;
    private int totalSectors = 0;
    private int sectorFactor = 0;

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
        return "Generation";
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

        for (int sx = 0; sx < totalSectors; sx++) {
            for (int sy = 0; sy < totalSectors; sy++) {
                if (sectorCounts[sx][sy] > 0) {

                    for (int x = 0; x < totalSectors; x++) {
                        for (int y = 0; y < totalSectors; y++) {

                            int blockX = x + 1 + totalSectors * sx;
                            int blockY = y + 1 + totalSectors * sy;

                            // skip when the current cell is dead
                            if (!matrix[blockX][blockY]) continue;

                            // loop over all the neighbors to increment neighbor count
                            heatmap[blockX - 1][blockY - 1]++;
                            heatmap[blockX - 1][blockY]++;
                            heatmap[blockX - 1][blockY + 1]++;

                            heatmap[blockX][blockY - 1]++;
                            heatmap[blockX][blockY + 1]++;

                            heatmap[blockX + 1][blockY - 1]++;
                            heatmap[blockX + 1][blockY]++;
                            heatmap[blockX + 1][blockY + 1]++;
                        }
                    }

                }

            }
        }
        return heatmap;
    }

    private void applyGameOfLifeRulesFromHeatmap(boolean[][] cells, byte[][] heatmap, short[][] sectorCounts) {
        for (int sx = 0; sx < totalSectors ; sx++) {
            for (int sy = 0; sy < totalSectors; sy++) {

                for (int x = 0; x < totalSectors; x++) {
                    for (int y = 0; y < totalSectors; y++) {

                        int blockX = x + 1 + totalSectors * sx;
                        int blockY = y + 1 + totalSectors * sy;

                        byte heat = heatmap[blockX][blockY];
                        if (!cells[blockX][blockY]) {
                            if (heat == 3) {
                                cells[blockX][blockY] = true;
                                sectorCounts[sx][sy]++;
                            }
                        } else {
                            if (!(heat == 3 || heat == 2)) {
                                cells[blockX][blockY] = false;
                                sectorCounts[sx][sy]--;
                            }
                        }
                    }
                }
            }
        }
    }
}