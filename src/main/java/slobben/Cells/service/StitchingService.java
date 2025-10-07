package slobben.Cells.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import slobben.Cells.entities.model.Block;
import slobben.Cells.entities.model.BorderInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
@Service
public class StitchingService {
    private final EnvironmentService environmentService;
    private Map<String, BorderInfo> borderCellMap;
    private int blockSize;
    private int blockSizeWithBorder;

    @PostConstruct
    private void postConstruct() {
        blockSize = environmentService.getBlockSize();
        blockSizeWithBorder = environmentService.getBlockSizeWithBorder();
        borderCellMap = new ConcurrentHashMap<>();
    }

    public void initializeStitch(Block block) {
        borderCellMap.put(key(block.getX(), block.getY()), new BorderInfo(blockSizeWithBorder));
    }

    public void resetStitch() {
        borderCellMap.clear();
    }

    public List<Block> addBorderCells(Block block) {
        ArrayList<Block> newBlocks = new ArrayList<>();
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                if (i == 0 && j == 0) continue;

                int neighborX = block.getX() + i;
                int neighborY = block.getY() + j;
                String neighborKey = key(neighborX, neighborY);

                BorderInfo neighborMap = borderCellMap.get(neighborKey);
                boolean hasNeigherMap = true;
                if (neighborMap == null) {
                    hasNeigherMap = false;
                    neighborMap = new BorderInfo(blockSizeWithBorder);
                } else {
                    neighborMap.setHasAliveCells(false);
                }

                boolean hasLiveCells = setBorderCellsForDirection(neighborMap, i, j, block.getCells());
                if (hasLiveCells) {
                    neighborMap.setHasAliveCells(true);
                }

                if (!hasNeigherMap && hasLiveCells) {
                    borderCellMap.put(neighborKey, neighborMap);
                    Block newBlock = new Block(neighborX, neighborY, new boolean[blockSizeWithBorder][blockSizeWithBorder]);
                    newBlocks.add(newBlock);
                }
            }
        }
        return newBlocks;
    }

    public void stitchBlock(Block block) {
        removeBorders(block.getCells());
        BorderInfo map = borderCellMap.get(key(block.getX(), block.getY()));
        if (map.isHasAliveCells()) {
            map.copyCells(block);
        }
    }

    private String key(int x, int y) {
        return x + "-" + y;
    }

    private boolean setBorderCellsForDirection(BorderInfo neighbourMap, int i, int j, boolean[][] cells) {
        switch (i + "," + j) {
            case "-1,-1": {
                boolean cell = cells[1][1];
                neighbourMap.setBottomRightCorner(cell);
                return cell;
            }
            case "-1,0": {
                var cellsToCopy = cells[1];
                var result = neighbourMap.setBottomBorder(cellsToCopy);
                return hasTrueValue(result);
            }
            case "-1,1": {
                boolean cell = cells[1][blockSize];
                neighbourMap.setBottomLeftCorner(cell);
                return cell;
            }
            case "0,-1": {
                var result = getColumnCells(cells, 1);
                neighbourMap.setRightBorder(result.getFirst());
                return result.getSecond();
            }
            case "0,1": {
                var result = getColumnCells(cells, blockSize);
                neighbourMap.setLeftBorder(result.getFirst());
                return result.getSecond();
            }
            case "1,-1": {
                boolean cell = cells[blockSize][1];
                neighbourMap.setTopRightCorner(cell);
                return cell;
            }
            case "1,0": {
                var cellsToCopy = new boolean[blockSizeWithBorder];
                System.arraycopy(cells[blockSize], 1, cellsToCopy, 1, blockSizeWithBorder - 1);
                var result = neighbourMap.setTopBorder(cellsToCopy);
                return hasTrueValue(result);
            }
            case "1,1": {
                boolean cell = cells[blockSize][blockSize];
                neighbourMap.setTopLeftCorner(cell);
                return cell;
            }
        }
        return false;
    }

    private boolean hasTrueValue(boolean[] cells) {
        for (var cell : cells) {
            if (cell) return true;
        }
        return false;
    }

    private Pair<boolean[], Boolean> getColumnCells(boolean[][] cells, int srcCol) {
        boolean[] cellsToCopy = new boolean[cells.length];
        boolean hasTrue = false;
        for (int i = 0; i < cells.length; i++) {
            cellsToCopy[i] = cells[i][srcCol];
            if (cells[i][srcCol]) {
                hasTrue = true;
            }
        }
        return Pair.of(cellsToCopy, hasTrue);
    }

    private void removeBorders(boolean[][] cells) {
        int max = blockSize + 1;

        // x keys
        cells[0] = new boolean[blockSizeWithBorder];
        cells[max] = new boolean[blockSizeWithBorder];

        // y keys
        for (int i = 0; i < cells[0].length; i++) {
            cells[i][0] = false;
            cells[i][max] = false;
        }
    }
}
