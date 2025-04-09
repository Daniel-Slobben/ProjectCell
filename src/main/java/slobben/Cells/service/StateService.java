package slobben.Cells.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import slobben.Cells.database.model.Cell;
import slobben.Cells.database.repository.CellRepository;
import slobben.Cells.enums.CellState;
import slobben.Cells.state.State;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static slobben.Cells.enums.CellState.ALIVE;
import static slobben.Cells.enums.CellState.DEAD;

@Service
@Getter
@Slf4j
public class StateService {

    private final CellRepository cellRepository;
    private final MongoTemplate mongoTemplate;
    private final int sizeX;
    private final int sizeY;
    private final int blockSize;
    private int currentGeneration = 0;

    public StateService(CellRepository cellRepository, MongoTemplate mongoTemplate, @Value("${properties.size.blockSize}") int blockSize, @Value("${properties.size.x}") int sizeX, @Value("${properties.size.y}") int sizeY) {
        this.mongoTemplate = mongoTemplate;
        mongoTemplate.dropCollection(Cell.class);

        this.cellRepository = cellRepository;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.blockSize = blockSize;

        ExecutorService executor = Executors.newFixedThreadPool(12);
        int blockAmountX = sizeX / blockSize;
        int blockAmountY = sizeY / blockSize;
        long totalTimerSetup = System.currentTimeMillis();

        for (int blockX = 0; blockX < blockAmountX; blockX++) {
            for (int blockY = 0; blockY < blockAmountY; blockY++) {
                int finalBlockX = blockX;
                int finalBlockY = blockY;
                Random random = new Random();
                executor.execute(() -> {
                    long addTimer = System.currentTimeMillis();
                    log.debug("Starting to generate Block X: {}, Y: {}", finalBlockY, finalBlockY);
                    ArrayList<Cell> cells = new ArrayList<>();
                    boolean makeAlive = random.nextInt(0, 10) == 0;

                    for (int x = 0; x < blockSize; x++) {
                        for (int y = 0; y < blockSize; y++) {
                            if (random.nextBoolean() && makeAlive) {
                                cells.add(new Cell(getCurrentGeneration(), x + (blockSize * finalBlockX), y + (blockSize * finalBlockY), ALIVE));
                            } else {
                                cells.add(new Cell(getCurrentGeneration(), x + (blockSize * finalBlockX), y + (blockSize * finalBlockY), DEAD));
                            }
                        }
                    }
                    log.debug("Generated Block X: {}, Y: {}, Time taken: {}ms", finalBlockY, finalBlockY, System.currentTimeMillis() - addTimer);
                    long saveTimer = System.currentTimeMillis();
                    log.debug("Starting to save Block X: {}, Y: {}", finalBlockY, finalBlockY);
                    cellRepository.saveAll(cells);
                    log.debug("Saved Block X: {}, Y: {}, Time taken: {}ms", finalBlockY, finalBlockY, System.currentTimeMillis() - saveTimer);
                });
            }
        } executor.close();
        System.out.println("Time taken: " + (System.currentTimeMillis() - totalTimerSetup));
    }
    public State getLatestState(int x, int y, int size) {
        long retrieveTimer = System.currentTimeMillis();
        int xMin = x - (size / 2);
        int xMax = x + (size / 2);
        int yMin = y - (size / 2);
        int yMax = y + (size / 2);
        log.debug("Retrieving cells: xmin: {}, xmax: {}, ymin: {}, ymax{}, size: {}", xMin, xMax, yMin, yMax, size);
        List<Optional<Cell>> flatCells = cellRepository.getMatrix(currentGeneration, xMin, xMax, yMin, yMax);
        log.debug("Retrieved Cells from mongodb: Time Taken: {} : xmin: {}, xmax: {}, ymin: {}, ymax{}, size: {}", System.currentTimeMillis() - retrieveTimer, xMin, xMax, yMin, yMax, size);
        Cell[][] partialMap = new Cell[size][size];

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                partialMap[i][j] = new Cell(x - (size / 2) + i, y - (size / 2) + j, CellState.EMPTY);
            }
        }

        log.debug("generatedPartialMap Time Taken: {} : xmin: {}, xmax: {}, ymin: {}, ymax{}, size: {}", System.currentTimeMillis() - retrieveTimer, xMin, xMax, yMin, yMax, size);

        flatCells.forEach(optionalCell -> {
            if (optionalCell.isPresent()) {
                Cell cell = optionalCell.get();
                int xIndex = cell.getX() - (x - (size / 2));
                int yIndex = cell.getY() - (y - (size / 2));
                partialMap[xIndex][yIndex] = cell;
            }
        });
        log.debug("Added Flatcells Time Taken: {} : xmin: {}, xmax: {}, ymin: {}, ymax{}, size: {}", System.currentTimeMillis() - retrieveTimer, xMin, xMax, yMin, yMax, size);

        return new State(partialMap);
    }

    public void incrementGeneration() {
        this.currentGeneration++;
    }

}
