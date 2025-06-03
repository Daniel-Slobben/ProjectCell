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
public class BoardManagingService {

    private final BlockRepository blockRepository;
    private final MongoTemplate mongoTemplate;
    private final int sizeX;
    private final int sizeY;
    private final int blockSize;
    private final int blockAmount;
    private final int blockSizeWithBorder;
    private int currentGeneration = 0;

    public BoardManagingService(BlockRepository blockRepository, MongoTemplate mongoTemplate, @Value("${properties.size.blockSize}") int blockSize, @Value("${properties.size.x}") int sizeX, @Value("${properties.size.y}") int sizeY) {
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
                                if (random.nextInt(0, 6) == 0) {
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
    }

    public Cell[][] getBlock(int x, int y) {
        return getBlock(blockRepository.findByXAndY(x, y));
    }

    public Cell[][] getBlockWithoutBorder(int x, int y) {
        return getBlockWithoutBorder(blockRepository.findByXAndY(x, y));
    }

    public Cell[][] getBlock(Block block) {
        Cell[][] partialMap = new Cell[blockSizeWithBorder][blockSizeWithBorder];
        var cells = block.getCells();
        for (var xEntry : cells.entrySet()) {
            for (var yEntry : xEntry.getValue().entrySet()) {
                partialMap[xEntry.getKey()][yEntry.getKey()] = yEntry.getValue();
            }
        }
        return partialMap;
    }

    public Cell[][] getBlockWithoutBorder(Block block) {
        var mapWithBorder = getBlock(block);
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
