package slobben.Cells.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import slobben.Cells.entities.model.Block;
import slobben.Cells.entities.model.Cell;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
@Service
public class StitchingService {
    private final EnvironmentService environmentService;
    private Map<String, Map<Integer, Map<Integer, Cell>>> borderCellMap;
    private int blockSize;

    @PostConstruct
    private void postConstruct() {
        blockSize = environmentService.getBlockSize();
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
                Map<Integer, Map<Integer, Cell>> neighborMap = borderCellMap.get(neighborKey);
                Map<Integer, Map<Integer, Cell>> borderCells = getBorderCellsForDirection(i, j, block.getCells());
                if (neighborMap != null) {
                    mergeNestedMaps(neighborMap, borderCells);
                } else if (!borderCells.isEmpty()) {
                    // neighbour block doesnt exit yet, but has bordercells. Time to create a new Block
                    Block newBlock = new Block(neighborX, neighborY);
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
        if (block.getCells() == null) {
            block.setCells(new HashMap<>());
        }
        removeBorders(block.getCells());
        Map<Integer, Map<Integer, Cell>> map = borderCellMap.get(key(block.getX(), block.getY()));
        if (map != null) {
            mergeNestedMaps(block.getCells(), map);
        }
    }

    private String key(int x, int y) {
        return x + "-" + y;
    }

    private void mergeNestedMaps(Map<Integer, Map<Integer, Cell>> target, Map<Integer, Map<Integer, Cell>> source) {
        for (var xEntry : source.entrySet()) {
            int x = xEntry.getKey();
            Map<Integer, Cell> innerSource = xEntry.getValue();
            Map<Integer, Cell> innerTarget = target.computeIfAbsent(x, k -> new ConcurrentHashMap<>());
            innerTarget.putAll(innerSource);
        }
    }

    private Map<Integer, Map<Integer, Cell>> getBorderCellsForDirection(int i, int j, Map<Integer, Map<Integer, Cell>> cells) {
        Map<Integer, Map<Integer, Cell>> result = new HashMap<>();

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

    private void copyColumn(Map<Integer, Map<Integer, Cell>> cells, Map<Integer, Map<Integer, Cell>> result, int srcCol, int destCol) {
        cells.forEach((rowIndex, rowMap) -> {
            if (rowMap == null) return;

            Cell cell = rowMap.get(srcCol);
            if (cell != null) {
                result.computeIfAbsent(rowIndex, k -> new HashMap<>()).put(destCol, cell);
            }
        });
    }

    private void copyRow(Map<Integer, Map<Integer, Cell>> cells, Map<Integer, Map<Integer, Cell>> result, int srcRow, int destRow) {
        Map<Integer, Cell> row = cells.get(srcRow);
        if (row == null) return;

        row.forEach((colIndex, cell) -> result.computeIfAbsent(destRow, k -> new HashMap<>()).put(colIndex, cell));
    }

    private void copyCornerCell(Map<Integer, Map<Integer, Cell>> cells, Map<Integer, Map<Integer, Cell>> result, int srcRow, int srcCol, int destRow, int destCol) {
        Map<Integer, Cell> row = cells.get(srcRow);
        if (row == null) return;

        Cell cell = row.get(srcCol);
        if (cell == null) return;

        result.computeIfAbsent(destRow, k -> new HashMap<>()).put(destCol, cell);
    }

    private void removeBorders(Map<Integer, Map<Integer, Cell>> blockCells) {
        int max = blockSize + 1;

        // x keys
        blockCells.remove(0);
        blockCells.remove(max);

        // y keys
        for (Map.Entry<Integer, Map<Integer, Cell>> xEntry : blockCells.entrySet()) {
            Map<Integer, Cell> row = xEntry.getValue();
            if (row != null) {
                row.remove(0);
                row.remove(max);
            }
        }
    }
}
