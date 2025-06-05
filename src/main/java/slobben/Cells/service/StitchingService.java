package slobben.Cells.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import slobben.Cells.database.model.Block;
import slobben.Cells.database.model.Cell;
import slobben.Cells.database.repository.BlockRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RequiredArgsConstructor
@Service
public class StitchingService {
    private final BlockRepository blockRepository;
    private final EnvironmentService environmentService;
    private final ConcurrentHashMap<String, Block> blocksToSave = new ConcurrentHashMap<>();
    // 9 is fully complete -> it is saved and then deleted
    private final ConcurrentHashMap<String, AtomicInteger> blockCompletenessMap = new ConcurrentHashMap<>();
    private Map<String, Map<Integer, Map<Integer, Cell>>> borderCellMap;
    private int blockSize;
    private int blockAmount;

    @PostConstruct
    private void postConstruct() {
        blockSize = environmentService.getBlockSize();
        blockAmount = environmentService.getBlockAmount();
    }

    public void initializeStich() {
        borderCellMap = new ConcurrentHashMap<>();
        // Initialize empty maps per block
        for (int blockX = 0; blockX < blockAmount; blockX++) {
            for (int blockY = 0; blockY < blockAmount; blockY++) {
                borderCellMap.put(key(blockX, blockY), new ConcurrentHashMap<>());
            }
        }
    }

    public void addBorderCells(Block block) {
        blocksToSave.put(key(block.getX(), block.getY()), block);
        initializeCompletenessMap(block);
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                if (i == 0 && j == 0) continue;

                int neighborX = block.getX() + i;
                int neighborY = block.getY() + j;
                String neighborKey = key(neighborX, neighborY);
                Map<Integer, Map<Integer, Cell>> neighborMap = borderCellMap.get(neighborKey);

                if (neighborMap != null) {
                    Map<Integer, Map<Integer, Cell>> borderCells = getBorderCellsForDirection(i, j, block.getCells());
                    mergeNestedMaps(neighborMap, borderCells);
                }
                blockCompletenessMap
                        .computeIfAbsent(neighborKey, k -> new AtomicInteger(0))
                        .incrementAndGet();
            }
        }
    }

    @SneakyThrows
    public void checkBlocksToSave() {
        List<String> readyBlockKeys = new ArrayList<>();
        blockCompletenessMap.forEach((key, value) -> {
            if (value.get() == 9) {
                readyBlockKeys.add(key);
            }
        });
        ExecutorService executor = Executors.newFixedThreadPool(24);
        readyBlockKeys.forEach(key -> {
            executor.submit(() -> {
                saveBlockWithNewBorders(blocksToSave.get(key));
            });
        });
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        blocksToSave.keySet().removeAll(readyBlockKeys);
        blockCompletenessMap.keySet().removeAll(readyBlockKeys);
    }

    private void initializeCompletenessMap(Block block) {
        int x = block.getX();
        int y = block.getY();

        boolean isLeft = x == 0;
        boolean isRight = x == blockAmount - 1;
        boolean isTop = y == 0;
        boolean isBottom = y == blockAmount - 1;
        int completeness = 1;

        if (isLeft || isRight || isTop || isBottom) {
            boolean isCorner = (isLeft || isRight) && (isTop || isBottom);

            if (isCorner) {
                completeness += 5;
            } else {
                completeness += 3;
            }
        }
        blockCompletenessMap
                .computeIfAbsent(key(x, y), k -> new AtomicInteger(0))
                .addAndGet(completeness);
    }

    private void saveBlockWithNewBorders(Block block) {
        removeBorders(block.getCells());
        Map<Integer, Map<Integer, Cell>> map = borderCellMap.get(key(block.getX(), block.getY()));
        if (map != null) {
            mergeNestedMaps(block.getCells(), map);
        }
        blockRepository.save(block);
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

        row.forEach((colIndex, cell) -> {
            result.computeIfAbsent(destRow, k -> new HashMap<>()).put(colIndex, cell);
        });
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

        // Collect x-keys to delete entirely (x == 0 or x == blockSize + 1)
        blockCells.remove(0);
        blockCells.remove(max);

        for (Map.Entry<Integer, Map<Integer, Cell>> xEntry : blockCells.entrySet()) {
            int x = xEntry.getKey();
            Map<Integer, Cell> row = xEntry.getValue();

            if (row != null) {
                row.remove(0);   // Remove left border
                row.remove(max); // Remove right border
            }
        }
    }
}
