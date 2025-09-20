package slobben.Cells.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import slobben.Cells.entities.model.Block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
@Service
public class StitchingService {
    private final EnvironmentService environmentService;
    private Map<String, Map<Integer, Map<Integer, Boolean>>> borderCellMap;
    private int blockSize;
    private int blockSizeWithBorder;

    @PostConstruct
    private void postConstruct() {
        blockSize = environmentService.getBlockSize();
        blockSizeWithBorder = environmentService.getBlockSizeWithBorder();
        borderCellMap = new ConcurrentHashMap<>();
    }

    public void initializeStitch(Block block) {
        borderCellMap.put(key(block.getX(), block.getY()), new ConcurrentHashMap<>());
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
                Map<Integer, Map<Integer, Boolean>> neighborMap = borderCellMap.get(neighborKey);
                Map<Integer, Map<Integer, Boolean>> borderCells = getBorderCellsForDirection(i, j, block.getCells());
                if (neighborMap != null) {
                    mergeNestedMaps(neighborMap, borderCells);
                } else if (!borderCells.isEmpty()) {
                    // neighbour block doesnt exit yet, but has bordercells. Time to create a new Block
                    Block newBlock = new Block(neighborX, neighborY, new boolean[blockSizeWithBorder][blockSizeWithBorder]);
                    newBlocks.add(newBlock);
                    initializeStitch(newBlock);
                    var newNeighborMap = borderCellMap.get(neighborKey);
                    mergeNestedMaps(newNeighborMap, borderCells);
                }
            }
        }
        return newBlocks;
    }

    public void stitchBlock(Block block) {
        removeBorders(block.getCells());
        Map<Integer, Map<Integer, Boolean>> map = borderCellMap.get(key(block.getX(), block.getY()));
        if (map != null) {
            map.forEach((rowIndex, rowMap) -> {
                rowMap.forEach((columnIndex, cell) -> block.getCells()[rowIndex][columnIndex] = cell);
            });
        }
    }

    private String key(int x, int y) {
        return x + "-" + y;
    }

    private void mergeNestedMaps(Map<Integer, Map<Integer, Boolean>> target, Map<Integer, Map<Integer, Boolean>> source) {
        for (var xEntry : source.entrySet()) {
            int x = xEntry.getKey();
            Map<Integer, Boolean> innerSource = xEntry.getValue();
            Map<Integer, Boolean> innerTarget = target.computeIfAbsent(x, k -> new ConcurrentHashMap<>());
            innerTarget.putAll(innerSource);
        }
    }

    private Map<Integer, Map<Integer, Boolean>> getBorderCellsForDirection(int i, int j, boolean[][] cells) {
        Map<Integer, Map<Integer, Boolean>> result = new HashMap<>();

        switch (i + "," + j) {
            case "-1,-1": {
                copyCornerCell(cells, result, 1, 1, blockSize + 1, blockSize + 1);
                break;
            }
            case "-1,0": {
                copyRow(cells, result, 1, blockSize + 1);
                break;
            }
            case "-1,1": {
                copyCornerCell(cells, result, 1, blockSize, blockSize + 1, 0);
                break;
            }
            case "0,-1": {
                copyColumn(cells, result, 1, blockSize + 1);
                break;
            }
            case "0,1": {
                copyColumn(cells, result, blockSize, 0);
                break;
            }
            case "1,-1": {
                copyCornerCell(cells, result, blockSize, 1, 0, blockSize + 1);
                break;
            }
            case "1,0": {
                copyRow(cells, result, blockSize, 0);
                break;
            }
            case "1,1": {
                copyCornerCell(cells, result, blockSize, blockSize, 0, 0);
                break;
            }
        }
        return result;
    }

    private void copyColumn(boolean[][] cells, Map<Integer, Map<Integer, Boolean>> result, int srcCol, int destCol) {
        for (int i = 0; i < cells.length; i++) {
            if (cells[i][srcCol]) {
                result.computeIfAbsent(i, k -> new HashMap<>()).put(destCol, Boolean.TRUE);
            }
        }
    }

    private void copyRow(boolean[][] cells, Map<Integer, Map<Integer, Boolean>> result, int srcRow, int destRow) {
        boolean[] row = cells[srcRow];

        for (int i = 0; i < row.length; i++) {
            if (row[i]) {
                result.computeIfAbsent(destRow, k -> new HashMap<>()).put(i, Boolean.TRUE);
            }
        }
    }

    private void copyCornerCell(boolean[][] cells, Map<Integer, Map<Integer, Boolean>> result, int srcRow, int srcCol, int destRow, int destCol) {
        if (!cells[srcRow][srcCol]) return;
        result.computeIfAbsent(destRow, k -> new HashMap<>()).put(destCol, Boolean.TRUE);
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
