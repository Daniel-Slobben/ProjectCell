package slobben.Cells.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import slobben.Cells.database.model.Block;
import slobben.Cells.database.model.Cell;
import slobben.Cells.database.repository.BlockRepository;
import slobben.Cells.database.repository.CellRepository;
import slobben.Cells.enums.CellState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
    private final BlockRepository blockRepository;
    private final Random random = new Random();

    public void setNextState() {
        //TODO: De volledige kaart opdelen in stukjes.
        // Die multithreaded afhalen door per stukje de cells op te halen in de database

        int generation = stateService.getCurrentGeneration();
        final int blockSize = stateService.getBlockSize();
        final int blockSizeWithBorder = blockSize + 2;
        int blockAmountX = stateService.getSizeX() / blockSize;
        int blockAmountY = stateService.getSizeY() / blockSize;

        ExecutorService executor = Executors.newFixedThreadPool(48);
        for (int blockX = 0; blockX < blockAmountX; blockX++) {
            for (int blockY = 0; blockY < blockAmountY; blockY++) {
                long generateTimer = System.currentTimeMillis();

                Block block  = blockRepository.findByXAndY(blockX, blockY);
                block.setGeneration(block.getGeneration() + 1);
                blockRepository.save(block);

                int finalBlockX = blockX;
                int finalBlockY = blockY;
                executor.execute(() -> {
                    ArrayList<Cell> newCells = new ArrayList<>();
                    ArrayList<Cell> removedCells = new ArrayList<>();

                    int middleOfBlockX = block.getX() * blockSize + (blockSize / 2);
                    int middleOfBlockY = block.getY() * blockSize + (blockSize / 2);
                    List<Cell> cells = stateService.getLatestState(middleOfBlockX, middleOfBlockY, blockSizeWithBorder);
                    if (!cells.isEmpty()) {

                        // there are alive cells, lets initialize matrix
                        Cell[][] partialMap = new Cell[blockSizeWithBorder][blockSizeWithBorder];
                        cells.forEach(cell -> {
                            int xIndex = cell.getX() - (middleOfBlockX - (blockSizeWithBorder / 2));
                            int yIndex = cell.getY() - (middleOfBlockY - (blockSizeWithBorder / 2));
                            partialMap[xIndex][yIndex] = cell;
                        });

                        // and check every cell
                        for (int x = 0; x < blockSize; x++) {
                            for (int y = 0; y < blockSize; y++) {
                                Cell oldCell = partialMap[x + 1][y + 1];
                                int neighboursAlive = getAliveNeighbourCount(3, x + 1, y + 1, partialMap);

                                // cell to check is dead
                                if (oldCell == null) {
                                    if (applyConwayGameOfLifeRules(DEAD, neighboursAlive).equals(ALIVE)) {
                                        newCells.add(new Cell(x + (blockSize * block.getX()), y + (blockSize * block.getY())));
                                    }
                                }
                                // cell to check is alive
                                else {
                                    if (applyConwayGameOfLifeRules(ALIVE, neighboursAlive).equals(DEAD)) {
                                        removedCells.add(oldCell);
                                    }
                                }
                            }
                        }
                        log.info("Calculated Block X: {}, Y: {}, Time taken: {}ms", finalBlockX, finalBlockY, System.currentTimeMillis() - generateTimer);
                        log.debug("Starting to save Block X: {}, Y: {}", finalBlockX, finalBlockY);
                        long saveTimer = System.currentTimeMillis();
                        cellRepository.saveAll(newCells);
                        cellRepository.deleteAll(removedCells);
                        log.info("Saved Block X: {}, Y: {}, Time taken: {}ms", finalBlockX, finalBlockY, System.currentTimeMillis() - saveTimer);
                    }
                });
            }
        }
        executor.close();
        stateService.incrementGeneration();
    }

    private CellState applyConwayGameOfLifeRules(CellState cellState, int aliveCounter) {
        if (cellState == ALIVE) {
            if (aliveCounter < 2) {
                return DEAD;
            } else if (aliveCounter == 2 || aliveCounter == 3) {
                return ALIVE;
            } else {
                return DEAD;
            }
        } else {
            if (aliveCounter == 3) {
                return ALIVE;
            } else {
                return DEAD;
            }
        }
    }

    private int getAliveNeighbourCount(int size, int x, int y, Cell[][] map) {
        int aliveCounter = 0;
        for (int j = 0; j < size; j++) {
            for (int i = 0; i < size; i++) {
                int xBuurman = x + (i - 1);
                int yBuurman = y + (j - 1);

                // alleen buurman/buurvrouw cellen
                if (!(xBuurman == x && yBuurman == y)) {
                    Cell cell = map[xBuurman][yBuurman];
                    if (cell != null) {
                        aliveCounter++;
                    }
                }
            }
        }
        return aliveCounter;
    }
}
