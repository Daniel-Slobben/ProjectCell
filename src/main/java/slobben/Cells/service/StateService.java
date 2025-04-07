package slobben.Cells.service;

import lombok.Getter;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static slobben.Cells.enums.CellState.DEAD;

@Service
@Getter
public class StateService {

    private final CellRepository cellRepository;
    private final MongoTemplate mongoTemplate;
    private final int sizeX;
    private final int sizeY;
    private final int blockSize;
    private int currentGeneration = 0;

    public StateService(CellRepository cellRepository, MongoTemplate mongoTemplate, @Value("${properties.size.blockSize}") int blockSize, @Value("${properties.size.x}") int sizeX, @Value("${properties.size.y}") int sizeY) {
        this.mongoTemplate = mongoTemplate;

        // TODO: mogelijk maken om een restart te doen en daarop verder te gaan
        cellRepository.deleteAll();
        this.cellRepository = cellRepository;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.blockSize = blockSize;

        ExecutorService executor = Executors.newFixedThreadPool(12);
        int blockAmountX = sizeX / blockSize;
        int blockAmountY = sizeY / blockSize;
        long timer = System.currentTimeMillis();

        for (int blockX = 0; blockX < blockAmountX; blockX++) {
            for (int blockY = 0; blockY < blockAmountY; blockY++) {
                int finalBlockX = blockX;
                int finalBlockY = blockY;
                executor.execute(() -> {
                    ArrayList<Cell> cells = new ArrayList<>();
                    for (int x = 0; x < blockSize; x++) {
                        for (int y = 0; y < blockSize; y++) {
                            cells.add(new Cell(getCurrentGeneration(), x + (blockSize * finalBlockX), y + (blockSize * finalBlockY), DEAD));
                        }
                    }
                    cellRepository.saveAll(cells);
                    System.out.println("saved block: x: " + finalBlockX + " y: " + finalBlockY);
                });
            }
        }
        executor.close();
        System.out.println("Time taken: " + (System.currentTimeMillis() - timer));
    }


    public State getLatestState(int x, int y, int size) {
        int xMin = x - (size / 2);
        int xMax = x + (size / 2);
        int yMin = y - (size / 2);
        int yMax = y + (size / 2);
        System.out.println("xmin: " + xMin + " xmax: " + xMax + " ymin: " + yMin + " ymax: " + yMax);
        List<Optional<Cell>> flatCells = cellRepository.getMatrix(currentGeneration, xMin, xMax, yMin, yMax);
        Cell[][] partialMap = new Cell[size][size];

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                partialMap[i][j] = new Cell(x - (size / 2) + i, y - (size / 2) + j, CellState.EMPTY);
            }
        }

        flatCells.forEach(optionalCell -> {
            if (optionalCell.isPresent()) {
                Cell cell = optionalCell.get();
                int xIndex = cell.getX() - (x - (size / 2));
                int yIndex = cell.getY() - (y - (size / 2));
                partialMap[xIndex][yIndex] = cell;
            }
        });

        return new State(partialMap);
    }

    public void incrementGeneration() {
        this.currentGeneration++;
    }
}
