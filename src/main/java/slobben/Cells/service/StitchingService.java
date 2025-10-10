package slobben.Cells.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import slobben.Cells.entities.model.Block;
import slobben.Cells.entities.model.BorderInfo;
import slobben.Cells.enums.Direction;

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
                }

                boolean hasLiveCells = setBorderCellsForDirection(neighborMap, i, j, block.getCells());
                if (hasLiveCells) {
                    neighborMap.setHasAliveCells(true);
                }

                if (!hasNeigherMap && hasLiveCells) {
                    borderCellMap.put(neighborKey, neighborMap);
                    Block newBlock = new Block(neighborX, neighborY, new byte[blockSizeWithBorder][blockSizeWithBorder]);
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


    private boolean setBorderCellsForDirection(BorderInfo neighbourMap, int i, int j, byte[][] cells) {
        Direction direction = Direction.from(i, j);
        assert direction != null;

        switch (direction) {
            case TOP_LEFT -> {
                byte cell = cells[1][1];
                neighbourMap.setBottomRightCorner(cell);
                return 0 != cell;
            }
            case TOP -> {
                byte[] cellsToCopy = cells[1];
                var result = neighbourMap.setBottomBorder(cellsToCopy);
                return hasTrueValue(result);
            }
            case TOP_RIGHT -> {
                byte cell = cells[1][blockSize];
                neighbourMap.setBottomLeftCorner(cell);
                return 0 != cell;
            }
            case LEFT -> {
                var result = getColumnCells(cells, 1);
                neighbourMap.setRightBorder(result.getFirst());
                return result.getSecond();
            }
            case RIGHT -> {
                var result = getColumnCells(cells, blockSize);
                neighbourMap.setLeftBorder(result.getFirst());
                return result.getSecond();
            }
            case BOTTOM_LEFT -> {
                byte cell = cells[blockSize][1];
                neighbourMap.setTopRightCorner(cell);
                return 0 != cell;
            }
            case BOTTOM -> {
                var cellsToCopy = new byte[blockSizeWithBorder];
                System.arraycopy(cells[blockSize], 1, cellsToCopy, 1, blockSizeWithBorder - 1);
                var result = neighbourMap.setTopBorder(cellsToCopy);
                return hasTrueValue(result);
            }
            case BOTTOM_RIGHT -> {
                byte cell = cells[blockSize][blockSize];
                neighbourMap.setTopLeftCorner(cell);
                return 0 != cell;
            }
        }
        return false;
    }

    private boolean hasTrueValue(byte[] cells) {
        for (var cell : cells) {
            if (0 != cell) return true;
        }
        return false;
    }

    private Pair<byte[], Boolean> getColumnCells(byte[][] cells, int srcCol) {
        byte[] cellsToCopy = new byte[cells.length];
        boolean hasTrue = false;
        for (int i = 0; i < cells.length; i++) {
            cellsToCopy[i] = cells[i][srcCol];
            if (0 != cells[i][srcCol]) {
                hasTrue = true;
            }
        }
        return Pair.of(cellsToCopy, hasTrue);
    }

    private void removeBorders(byte[][] cells) {
        int max = blockSize + 1;

        // x keys
        cells[0] = new byte[blockSizeWithBorder];
        cells[max] = new byte[blockSizeWithBorder];

        // y keys
        for (int i = 0; i < cells[0].length; i++) {
            cells[i][0] = 0;
            cells[i][max] = 0;
        }
    }
}
