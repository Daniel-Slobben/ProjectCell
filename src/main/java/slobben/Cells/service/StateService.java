package slobben.Cells.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import slobben.Cells.database.model.Block;
import slobben.Cells.database.model.Cell;
import slobben.Cells.database.repository.BlockRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private int currentGeneration = 0;

    public StateService(BlockRepository blockRepository, MongoTemplate mongoTemplate, @Value("${properties.size.blockSize}") int blockSize, @Value("${properties.size.x}") int sizeX, @Value("${properties.size.y}") int sizeY) {
        this.mongoTemplate = mongoTemplate;

        this.blockRepository = blockRepository;
        this.sizeX = sizeX;
        this.blockSize = blockSize;
        this.sizeY = sizeY;
        this.blockAmount = sizeY / blockSize;

        initializeMap(blockRepository, blockSize, sizeX, sizeY);
    }

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
                    ArrayList<Cell> cells = new ArrayList<>();
                    if (random.nextInt(0, 10) == 0) {
                        long addTimer = System.currentTimeMillis();
                        log.debug("Starting to generate Block X: {}, Y: {}", finalBlockY, finalBlockY);
                        for (int x = 0; x < blockSize; x++) {
                            for (int y = 0; y < blockSize; y++) {
                                if (random.nextBoolean()) {
                                    cells.add(new Cell(x + (blockSize * finalBlockX), y + (blockSize * finalBlockY)));
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
        System.out.println("Time taken to generate and save block: " + (System.currentTimeMillis() - totalTimerSetup));
        totalTimerSetup = System.currentTimeMillis();
        stitch();
        System.out.println("Time taken to stitch: " + (System.currentTimeMillis() - totalTimerSetup));
    }

    private void stitch() {
        HashMap<String, ArrayList<Cell>> borderCellMap = new HashMap<>();

        for (int blockX = 0; blockX < blockAmount; blockX++) {
            for (int blockY = 0; blockY < blockAmount; blockY++) {
                borderCellMap.put(blockX + "-" + blockY, new ArrayList<>());
            }
        }

        for (int blockX = 0; blockX < blockAmount; blockX++) {
            for (int blockY = 0; blockY < blockAmount; blockY++) {
                for (int i = -1; i < 2; i++) {
                    for (int j = -1; j < 2; j++) {
                        if (!(i == 0 && j == 0)) {
                            ArrayList<Cell> blockToAdd = borderCellMap.get((blockX + i) + "-" + (blockY + j));
                            if (blockToAdd != null) {
                                blockToAdd.addAll(getBorderCellsForDirection(i, j, blockSize, blockToAdd));
                            }
                        }
                    }
                }
            }
        }

        for (int blockX = 0; blockX < blockAmount; blockX++) {
            List<Block> blocks = blockRepository.findByX(blockX);
            blocks.forEach(block -> block.getCells().addAll(borderCellMap.get(block.getX() + "-" + block.getY())));
            blockRepository.saveAll(blocks);
        }

    }

    private List<Cell> getBorderCellsForDirection(int i, int j, int blockSize, List<Cell> cells) {
        int max = blockSize - 1;

        if (i == -1 && j == -1)
            return cells.stream().filter(c -> c.getX() == 0 && c.getY() == 0).toList();        // Top-left
        if (i == -1 && j == 0)
            return cells.stream().filter(c -> c.getX() == 0).toList();                         // Left
        if (i == -1 && j == 1)
            return cells.stream().filter(c -> c.getX() == 0 && c.getY() == max).toList();      // Bottom-left
        if (i == 0 && j == -1) return cells.stream().filter(c -> c.getY() == 0).toList();                         // Top
        if (i == 0 && j == 1)
            return cells.stream().filter(c -> c.getY() == max).toList();                       // Bottom
        if (i == 1 && j == -1)
            return cells.stream().filter(c -> c.getX() == max && c.getY() == 0).toList();      // Top-right
        if (i == 1 && j == 0)
            return cells.stream().filter(c -> c.getX() == max).toList();                       // Right
        if (i == 1 && j == 1)
            return cells.stream().filter(c -> c.getX() == max && c.getY() == max).toList();    // Bottom-right

        return List.of();
    }

    public List<Cell> getLatestState(int x, int y, int size) {
        long retrieveTimer = System.currentTimeMillis();
        List<Cell> cells = blockRepository.findByXAndY(x, y).getCells();
        log.info("Retrieved Cells from mongodb: Time Taken: {} ", System.currentTimeMillis() - retrieveTimer);
        return cells;
    }

    public Cell[][] getMatrixState(int xToFind, int yToFind, int size) {
        List<Cell> cellList = this.getLatestState(xToFind, yToFind, size);
        Cell[][] cells = new Cell[size][size];
        cellList.forEach(cell -> cells[cell.getX()][cell.getY()] = cell);
        return cells;
    }

    public void incrementGeneration() {
        this.currentGeneration++;
    }
}
