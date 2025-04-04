package slobben.Cells.service;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import slobben.Cells.database.model.Cell;
import slobben.Cells.database.repository.CellRepository;
import slobben.Cells.enums.CellState;
import slobben.Cells.state.State;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static slobben.Cells.enums.CellState.DEAD;

@Service
@Getter
public class StateService {

    private final CellRepository cellRepository;
    private int currentGeneration = 0;
    private final MongoTemplate mongoTemplate;

    private final int sizeX;
    private final int sizeY;

    public StateService(CellRepository cellRepository, MongoTemplate mongoTemplate, @Value("${properties.size.x}") int sizeX, @Value("${properties.size.y}") int sizeY) {
        this.mongoTemplate = mongoTemplate;

        // TODO: mogelijk maken om een restart te doen en daarop verder te gaan
        cellRepository.deleteAll();
        this.cellRepository = cellRepository;
        this.sizeX = sizeX;
        this.sizeY = sizeY;

        final int rowBatchSize = 20;

        ExecutorService executor = Executors.newFixedThreadPool(12);
        for (int x = 0; x < sizeX; x++) {
            executeBatch(executor, x, rowBatchSize);
            x += rowBatchSize -1;
        }
        executor.close();
        System.out.println("total document count: " + cellRepository.count());
    }

    private void executeBatch(ExecutorService executor, int x, int rowBatchSize) {
        executor.execute(() -> {
            List<Cell> xList = new ArrayList<>();

            for (int i = 0; i < rowBatchSize; i++) {
                int currentX = x + i;
                for (int y = 0; y < sizeY; y++) {
                    xList.add(new Cell(currentGeneration, currentX, y, DEAD));
                }
            }
            cellRepository.saveAll(xList);
            System.out.println("Saved at row: " + (x + rowBatchSize));
        });
    }

    public State getLatestState(int x, int y, int size) {
        List<Cell> flatCells = cellRepository.findSubsetMatrix(currentGeneration, x - (size / 2), x + (size / 2), y - (size / 2), y + (size / 2));
        Cell[][] partialMap = new Cell[size][size];

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                partialMap[i][j] = new Cell(x - (size / 2) + i, y - (size / 2) + j, CellState.EMPTY);
            }
        }

        flatCells.forEach(cell -> {
            int xIndex = cell.getX() - (x - (size / 2));
            int yIndex = cell.getY() - (y - (size / 2));
            partialMap[xIndex][yIndex] = cell;
        });

        return new State(partialMap);
    }

    public void incrementGeneration() {
        this.currentGeneration++;
    }
}
