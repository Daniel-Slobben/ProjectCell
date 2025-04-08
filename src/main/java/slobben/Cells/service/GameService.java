package slobben.Cells.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import slobben.Cells.database.model.Cell;
import slobben.Cells.database.repository.CellRepository;
import slobben.Cells.enums.CellState;

import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static slobben.Cells.enums.CellState.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class GameService {

    private final StateService stateService;
    private final CellRepository cellRepository;
    private final Random random = new Random();

    public void setNextState() {
        int generation = stateService.getCurrentGeneration();
        final int blockSize = stateService.getBlockSize();
        int blockAmountX = stateService.getSizeX() / blockSize;
        int blockAmountY = stateService.getSizeY() / blockSize;

        ExecutorService executor = Executors.newFixedThreadPool(12);
        for (int blockX = 0; blockX < blockAmountX; blockX++) {
            for (int blockY = 0; blockY < blockAmountY; blockY++) {
                long generateTimer = System.currentTimeMillis();

                int finalBlockX = blockX;
                int finalBlockY = blockY;

                log.info("Starting to calculate Block X: {}, Y: {}", finalBlockY, finalBlockY);
                ArrayList<Cell> cells = new ArrayList<>();
                int middleOfBlockX = finalBlockX * blockSize + (blockSize / 2);
                int middleOfBlockY = finalBlockY * blockSize + (blockSize / 2);
                Cell[][] latestMap = stateService.getLatestState(middleOfBlockX, middleOfBlockY, blockSize + 2).state();
                for (int x = 0; x < blockSize; x++) {
                    for (int y = 0; y < blockSize; y++) {
                        int actualX = finalBlockX * blockSize + x;
                        int actualY = finalBlockY * blockSize + y;

                        int neighboursAlive = getAliveNeighbourCount(3, x + 1, y + 1, latestMap);
                        cells.add(applyConwayGameOfLifeRules(latestMap[x + 1][y + 1].getCellState(), actualX, actualY, neighboursAlive, generation + 1));
                    }
                }
                log.info("Calculated Block X: {}, Y: {}, Time taken: {}ms", finalBlockY, finalBlockY, System.currentTimeMillis() - generateTimer);
                log.info("Starting to save Block X: {}, Y: {}", finalBlockY, finalBlockY);
                long saveTimer = System.currentTimeMillis();
                cellRepository.saveAll(cells);
                log.info("Saved Block X: {}, Y: {}, Time taken: {}ms", finalBlockY, finalBlockY, System.currentTimeMillis() - saveTimer);
            }
        }
        executor.close();
        stateService.incrementGeneration();
    }

    private Cell applyConwayGameOfLifeRules(CellState cellState, int x, int y, int aliveCounter, int generation) {
        if (cellState == ALIVE) {
            if (aliveCounter < 2) {
                return new Cell(generation, x, y, DEAD);
            } else if (aliveCounter == 2 || aliveCounter == 3) {
                return new Cell(generation, x, y, ALIVE);
            } else {
                return new Cell(generation, x, y, DEAD);
            }
        } else {
            if (aliveCounter == 3) {
                return new Cell(generation, x, y, ALIVE);
            } else {
                return new Cell(generation, x, y, DEAD);
            }
        }
    }

    private int getAliveNeighbourCount(int size, int x, int y, Cell[][] map) {
        int aliveCounter = 0;
        for (int j = 0; j < size; j++) {
            for (int i = 0; i < size; i++) {
                int xBuurman = x + (i - 1);
                int yBuurman = y + (j - 1);

                CellState cellState = map[xBuurman][yBuurman].getCellState();
                if (cellState == ALIVE) {
                    aliveCounter++;
                } else if (cellState == EMPTY) {
                    if (random.nextBoolean()) {
                        aliveCounter++;
                    }
                }
            }
        }
        return aliveCounter;
    }
}
