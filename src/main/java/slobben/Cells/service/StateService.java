package slobben.Cells.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import slobben.Cells.database.model.Block;
import slobben.Cells.database.model.Cell;
import slobben.Cells.database.repository.BlockRepository;
import slobben.Cells.database.repository.CellRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Getter
@Slf4j
public class StateService {

    private final CellRepository cellRepository;
    private final BlockRepository blockRepository;
    private final MongoTemplate mongoTemplate;
    private final int sizeX;
    private final int sizeY;
    private final int blockSize;
    private int currentGeneration = 0;

    public StateService(CellRepository cellRepository, BlockRepository blockRepository, MongoTemplate mongoTemplate, @Value("${properties.size.blockSize}") int blockSize, @Value("${properties.size.x}") int sizeX, @Value("${properties.size.y}") int sizeY) {
        this.mongoTemplate = mongoTemplate;

        this.cellRepository = cellRepository;
        this.blockRepository = blockRepository;
        this.sizeX = sizeX;
        this.blockSize = blockSize;
        this.sizeY = sizeY;

        initializeMap(cellRepository, blockRepository, blockSize, sizeX, sizeY);
    }

    private void initializeMap(CellRepository cellRepository, BlockRepository blockRepository, int blockSize, int sizeX, int sizeY) {
        mongoTemplate.dropCollection(Cell.class);
        mongoTemplate.dropCollection(Block.class);
        int blockAmountX = sizeX / blockSize;
        int blockAmountY = sizeY / blockSize;
        ExecutorService executor = Executors.newFixedThreadPool(24);
        long totalTimerSetup = System.currentTimeMillis();

        for (int blockX = 0; blockX < blockAmountX; blockX++) {
            for (int blockY = 0; blockY < blockAmountY; blockY++) {
                int finalBlockX = blockX;
                int finalBlockY = blockY;
                blockRepository.save(new Block(finalBlockX, finalBlockY, 0));
                executor.execute(() -> {
                    long addTimer = System.currentTimeMillis();
                    log.info("Starting to generate Block X: {}, Y: {}", finalBlockY, finalBlockY);
                    ArrayList<Cell> cells = new ArrayList<>();
                    for (int x = 0; x < blockSize; x++) {
                        for (int y = 0; y < blockSize; y++) {
                            cells.add(new Cell(getCurrentGeneration(), x + (blockSize * finalBlockX), y + (blockSize * finalBlockY), DEAD));
                        }
                    }
                    log.info("Generated Block X: {}, Y: {}, Time taken: {}ms", finalBlockY, finalBlockY, System.currentTimeMillis() - addTimer);
                    long saveTimer = System.currentTimeMillis();
                    log.info("Starting to save Block X: {}, Y: {}", finalBlockY, finalBlockY);
                    cellRepository.saveAll(cells);
                    log.info("Saved Block X: {}, Y: {}, Time taken: {}ms", finalBlockY, finalBlockY, System.currentTimeMillis() - saveTimer);
                    Random random = new Random();
                    List<Cell> newCells = new ArrayList<>();

                    cellRepository.saveAll(newCells);
                });
            }
        }
        executor.close();
        System.out.println("Time taken: " + (System.currentTimeMillis() - totalTimerSetup));
    }

    public List<Cell> getLatestState(int x, int y, int size) {
        long retrieveTimer = System.currentTimeMillis();
        int xMin = x - (size / 2);
        int xMax = x + (size / 2);
        int yMin = y - (size / 2);
        int yMax = y + (size / 2);
        List<Cell> cells = cellRepository.getMatrix(xMin, xMax, yMin, yMax);
        log.info("Retrieved Cells from mongodb: Time Taken: {} : xmin: {}, xmax: {}, ymin: {}, ymax{}, size: {}", System.currentTimeMillis() - retrieveTimer, xMin, xMax, yMin, yMax, size);
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
