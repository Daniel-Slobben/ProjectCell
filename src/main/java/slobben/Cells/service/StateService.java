package slobben.Cells.service;

import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import slobben.Cells.database.model.Block;
import slobben.Cells.database.model.Cell;
import slobben.Cells.database.repository.BlockRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
@Getter
@Slf4j
public class StateService {

    private final BlockRepository blockRepository;
    private final MongoTemplate mongoTemplate;
    private final int sizeX;
    private final int sizeY;
    private final int blockSize;
    private final int blockAmount;
    private final int blockSizeWithBorder;
    private int currentGeneration = 0;

    public StateService(BlockRepository blockRepository, MongoTemplate mongoTemplate, @Value("${properties.size.blockSize}") int blockSize, @Value("${properties.size.x}") int sizeX, @Value("${properties.size.y}") int sizeY) {
        this.mongoTemplate = mongoTemplate;

        this.blockRepository = blockRepository;
        this.sizeX = sizeX;
        this.blockSize = blockSize;
        this.sizeY = sizeY;
        this.blockAmount = sizeY / blockSize;
        this.blockSizeWithBorder = blockSize + 2;

        initializeMap(blockRepository, blockSize, sizeX, sizeY);
    }

    @SneakyThrows
    private void initializeMap(BlockRepository blockRepository, int blockSize, int sizeX, int sizeY) {
        mongoTemplate.dropCollection(Cell.class);
        mongoTemplate.dropCollection(Block.class);
        ExecutorService executor = Executors.newFixedThreadPool(24);
        long totalTimerSetup = System.currentTimeMillis();

        for (int blockX = 0; blockX < blockAmount; blockX++) {
            for (int blockY = 0; blockY < blockAmount; blockY++) {
                int finalBlockX = blockX;
                int finalBlockY = blockY;
                Block block = new Block(finalBlockX, finalBlockY, 0);
                executor.execute(() -> {
                    Random random = new Random();
                    Map<Integer, Map<Integer, Cell>> cells = new HashMap<>();
                    if (random.nextInt(0, 1) == 0) {
                        long addTimer = System.currentTimeMillis();
                        log.debug("Starting to generate Block X: {}, Y: {}", finalBlockY, finalBlockY);
                        for (int x = 0; x < blockSize; x++) {
                            for (int y = 0; y < blockSize; y++) {
                                if (random.nextBoolean()) {
                                    Cell cell = new Cell(x + (blockSize * finalBlockX), y + (blockSize * finalBlockY));
                                    cells.computeIfAbsent(x + 1, row -> new HashMap<>()).put(y + 1, cell);
                                }
                            }
                        }
                        log.debug("Generated Block X: {}, Y: {}, Time taken: {}ms", finalBlockY, finalBlockY, System.currentTimeMillis() - addTimer);
                    }
                    long saveTimer = System.currentTimeMillis();
                    log.debug("Starting to save Block X: {}, Y: {}", finalBlockY, finalBlockY);
                    block.setCells(cells);
                    blockRepository.save(block);
                    log.debug("Saved Block X: {}, Y: {}, Time taken: {}ms", finalBlockY, finalBlockY, System.currentTimeMillis() - saveTimer);
                });
            }
        }
        executor.close();
        executor.awaitTermination(120, TimeUnit.SECONDS);
        System.out.println("Time taken to generate: " + (System.currentTimeMillis() - totalTimerSetup));
        stitch();
    }

    @SneakyThrows
    void stitch() {
        log.info("Starting Stitch for generation {}", currentGeneration);
        ExecutorService executor = Executors.newFixedThreadPool(24);
        long totalTimerSetup = System.currentTimeMillis();
        Map<String, Map<Integer, Map<Integer, Cell>>> borderCellMap = new ConcurrentHashMap<>();

        // Initialize empty maps per block
        for (int blockX = 0; blockX < blockAmount; blockX++) {
            for (int blockY = 0; blockY < blockAmount; blockY++) {
                borderCellMap.put(key(blockX, blockY), new ConcurrentHashMap<>());
            }
        }

        // Build border maps for each neighbor
        for (int blockX = 0; blockX < blockAmount; blockX++) {
            for (int blockY = 0; blockY < blockAmount; blockY++) {
                int finalBlockX = blockX;
                int finalBlockY = blockY;
                executor.execute(() -> {
                    Map<Integer, Map<Integer, Cell>> currentMap = blockRepository.findByXAndY(finalBlockX, finalBlockY).getCells();

                    for (int i = -1; i <= 1; i++) {
                        for (int j = -1; j <= 1; j++) {
                            if (i == 0 && j == 0) continue;

                            int neighborX = finalBlockX + i;
                            int neighborY = finalBlockY + j;
                            String neighborKey = key(neighborX, neighborY);
                            Map<Integer, Map<Integer, Cell>> neighborMap = borderCellMap.get(neighborKey);

                            if (neighborMap != null) {
                                Map<Integer, Map<Integer, Cell>> borderCells = getBorderCellsForDirection(i, j, currentMap);
                                mergeNestedMaps(neighborMap, borderCells);
                            }
                        }
                    }
                });
            }
        }
        executor.shutdown();
        executor.awaitTermination(120, TimeUnit.SECONDS);

        ExecutorService saveExecutor = Executors.newFixedThreadPool(24);

        // Stitch border cells into real blocks
        for (int blockX = 0; blockX < blockAmount; blockX++) {
            int finalBlockX = blockX;
            saveExecutor.execute(() -> {
                List<Block> blocks = blockRepository.findByX(finalBlockX);
                for (Block block : blocks) {
                    removeBorders(block.getCells());
                    Map<Integer, Map<Integer, Cell>> map = borderCellMap.get(key(block.getX(), block.getY()));
                    if (map != null) {
                        mergeNestedMaps(block.getCells(), map);
                    }
                }
                blockRepository.saveAll(blocks);
            });
        }
        saveExecutor.shutdown();
        saveExecutor.awaitTermination(120, TimeUnit.SECONDS);
        System.out.println("Time taken to stitch: " + (System.currentTimeMillis() - totalTimerSetup));
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

    private void copyColumn(Map<Integer, Map<Integer, Cell>> cells,
                            Map<Integer, Map<Integer, Cell>> result,
                            int srcCol, int destCol) {
        cells.forEach((rowIndex, rowMap) -> {
            if (rowMap == null) return;

            Cell cell = rowMap.get(srcCol);
            if (cell != null) {
                result.computeIfAbsent(rowIndex, k -> new HashMap<>())
                        .put(destCol, cell);
            }
        });
    }

    private void copyRow(Map<Integer, Map<Integer, Cell>> cells,
                         Map<Integer, Map<Integer, Cell>> result,
                         int srcRow, int destRow) {
        Map<Integer, Cell> row = cells.get(srcRow);
        if (row == null) return;

        row.forEach((colIndex, cell) -> {
            result.computeIfAbsent(destRow, k -> new HashMap<>())
                    .put(colIndex, cell);
        });
    }

    private void copyCornerCell(Map<Integer, Map<Integer, Cell>> cells,
                                Map<Integer, Map<Integer, Cell>> result,
                                int srcRow, int srcCol,
                                int destRow, int destCol) {
        Map<Integer, Cell> row = cells.get(srcRow);
        if (row == null) return;

        Cell cell = row.get(srcCol);
        if (cell == null) return;

        result.computeIfAbsent(destRow, k -> new HashMap<>())
                .put(destCol, cell);
    }

    public Cell[][] getBlock(int x, int y) {
        Cell[][] partialMap = new Cell[blockSizeWithBorder][blockSizeWithBorder];
        Map<Integer, Map<Integer, Cell>> cells = blockRepository.findByXAndY(x, y).getCells();
        for (var xEntry : cells.entrySet()) {
            for (var yEntry : xEntry.getValue().entrySet()) {
                partialMap[xEntry.getKey()][yEntry.getKey()] = yEntry.getValue();
            }
        }
        return partialMap;
    }

    public Cell[][] getBlockWithoutBorder(int x, int y) {
       var mapWithBorder = getBlock(x, y);
       Cell[][] map = new Cell[blockSize][blockSize];
       for (int i = 1; i < blockSizeWithBorder - 1; i++) {
           for (int j = 1; j < blockSizeWithBorder - 1; j++) {
               map[i-1][j-1] = mapWithBorder[i][j];
           }
       }
       return map;
    }

    public void incrementGeneration() {
        this.currentGeneration++;
    }
}
