package slobben.Cells.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import slobben.Cells.enums.CellState;
import slobben.Cells.database.model.Cell;
import slobben.Cells.state.State;

import java.util.Random;

import static slobben.Cells.enums.CellState.ALIVE;
import static slobben.Cells.enums.CellState.DEAD;

@Service
@RequiredArgsConstructor
public class GameService {

    private final StateService stateService;
    private final InputService inputService;
    private final Random random = new Random();

    public void setNextState() {
        //TODO: De volledige kaart opdelen in stukjes.
        // Die multithreaded afhalen door per stukje de cells op te halen in de database

        stateService.incrementGeneration();
        int generation = stateService.getCurrentGeneration();
        final int blockSize = 100;
        int sizeX = stateService.getSizeX();
        int sizeY = stateService.getSizeY();


//
//        Cell[][] nextMap = new Cell[sizeX][sizeY];
//        for (int x = 0; x < sizeX; x++) {
//            for (int y = 0; y < sizeY; y++) {
//                int amountAlive = getAliveNeighbourCount(3, x, y, sizeX, sizeY, map);
//                applyConwayGameOfLifeRules(map[x][y].getCellState(), x, y, amountAlive, nextMap);
//            }
//        }
//        stateService.addState(new State(nextMap));
//        inputService.resetOverlay();
    }

    private void applyConwayGameOfLifeRules(CellState cellState, int x, int y, int aliveCounter, Cell[][] nextMap) {
        if (cellState == ALIVE) {
            if (aliveCounter < 2) {
                nextMap[x][y] = new Cell(stateService.getCurrentGeneration(), x, y, DEAD);
            } else if (aliveCounter == 2 || aliveCounter == 3) {
                nextMap[x][y] = new Cell(stateService.getCurrentGeneration(), x, y, ALIVE);
            } else {
                nextMap[x][y] = new Cell(stateService.getCurrentGeneration(), x, y, DEAD);
            }
        } else {
            if (aliveCounter == 3) {
                nextMap[x][y] = new Cell(stateService.getCurrentGeneration(), x, y, ALIVE);
            } else {
                nextMap[x][y] = new Cell(stateService.getCurrentGeneration(), x, y, DEAD);
            }
        }
        stateService.incrementGeneration();
    }

    private int getAliveNeighbourCount(int size, int x, int y, int sizeX, int sizeY, Cell[][] map) {
        int aliveCounter = 0;
        for (int j = 0; j < size; j++) {
            for (int i = 0; i < size; i++) {
                int xBuurman = x + (i - 1);
                int yBuurman = y + (j - 1);
                if (xBuurman < 0 || yBuurman < 0 || xBuurman >= sizeX || yBuurman >= sizeY) {
                    if (random.nextBoolean()) {
                        aliveCounter++;
                    }
                } else {
                    if (map[xBuurman][yBuurman].getCellState() == ALIVE) {
                        aliveCounter++;
                    }
                }
            }
        }
        return aliveCounter;
    }
}
