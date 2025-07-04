package slobben.Cells.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import slobben.Cells.entities.model.Block;
import slobben.Cells.entities.model.Cell;
import slobben.Cells.enums.CellState;

import java.util.HashMap;
import java.util.Map;

import static slobben.Cells.enums.CellState.ALIVE;
import static slobben.Cells.enums.CellState.DEAD;

@Service
@Slf4j
@RequiredArgsConstructor
public class GenerationService {

    private final BoardInfoService boardInfoService;
    private final EnvironmentService environmentService;
    private int blockSize;

    @PostConstruct
    public void init() {
       blockSize = environmentService.getBlockSize();
    }

    @SneakyThrows
    public void setNextState(Block block) {
        block.setGeneration(block.getGeneration() + 1);
        if (!block.getCells().isEmpty()) {
            Cell[][] cellsCopy = boardInfoService.getBlock(block);

            // Run game rules
            for (int x = 1; x < blockSize + 1; x++) {
                int carryOver1 = -1;
                int carryOver2 = -1;

                for (int y = 1; y < blockSize + 1; y++) {
                    Cell oldCell = cellsCopy[x][y];
                    int[] neighboursAlive = getAliveNeighbourCount(x, y, cellsCopy, carryOver1, carryOver2);
                    carryOver1 = neighboursAlive[1];
                    carryOver2 = neighboursAlive[2];

                    int globalX = (x - 1) + (blockSize * block.getX());
                    int globalY = (y - 1) + (blockSize * block.getY());

                    // If cell was dead
                    if (oldCell == null) {
                        if (applyConwayGameOfLifeRules(DEAD, neighboursAlive[0]).equals(ALIVE)) {
                            insertCell(block.getCells(), new Cell(globalX, globalY), x, y);
                        }
                    }
                    // If cell was alive
                    else {
                        if (applyConwayGameOfLifeRules(ALIVE, neighboursAlive[0]).equals(DEAD)) {
                            removeCell(block.getCells(), x, y);
                        }
                    }
                }
            }
        }
    }

    @SneakyThrows
    public void setNextStateNew(Block block) {
        int blockSizeWithBorder = environmentService.getBlockSizeWithBorder();
        int[][] heatmap = new int[blockSizeWithBorder][blockSizeWithBorder];
        boolean[][] aliveMap = new boolean[blockSizeWithBorder][blockSizeWithBorder];
        block.getCells().forEach((x, yRow) -> yRow.forEach((y, cell) -> {
            aliveMap[x][y] = true;
            if (!(x == 0 || x == blockSizeWithBorder - 1 || y == 0 || y == blockSizeWithBorder - 1)) {
                for (int i = -1; i <= 1; i++) {
                    for (int j = -1; j <= 1; j++) {
                        if (i == 0 && j == 0) continue;
                        int neighborX = x + i;
                        int neighborY = y + j;
                        if (neighborX < blockSizeWithBorder && neighborY >= 0 && neighborY < blockSizeWithBorder) {
                            heatmap[neighborX][neighborY]++;
                        }
                    }
                }
            }
        }));
        for (int x = 1; x < blockSize + 1; x++) {
            for (int y = 1; y < blockSize + 1; y++) {
                int globalX = (x - 1) + (blockSize * block.getX());
                int globalY = (y - 1) + (blockSize * block.getY());
                // If cell was dead
                if (!aliveMap[x][y]) {
                    if (applyConwayGameOfLifeRules(DEAD, heatmap[x][y]).equals(ALIVE)) {
                        insertCell(block.getCells(), new Cell(globalX, globalY), x, y);
                    }
                }
                // If cell was alive
                else {
                    if (applyConwayGameOfLifeRules(ALIVE, heatmap[x][y]).equals(DEAD)) {
                        removeCell(block.getCells(), x, y);
                    }
                }
            }
        }
    }

    private void insertCell(Map<Integer, Map<Integer, Cell>> cellMap, Cell cell, int x, int y) {
        cellMap.computeIfAbsent(x, value -> new HashMap<>()).put(y, cell);
    }

    private void removeCell(Map<Integer, Map<Integer, Cell>> cellMap, int x, int y) {
        Map<Integer, Cell> row = cellMap.get(x);
        if (row != null) {
            row.remove(y);
            if (row.isEmpty()) {
                cellMap.remove(x);
            }
        }
    }

    private CellState applyConwayGameOfLifeRules(CellState cellState, int aliveCounter) {
        if (cellState == ALIVE) {
            return (aliveCounter == 2 || aliveCounter == 3) ? ALIVE : DEAD;
        } else {
            return (aliveCounter == 3) ? ALIVE : DEAD;
        }
    }

    private int[] getAliveNeighbourCount(int x, int y, Cell[][] map, int carryOver1, int carryOver2) {
        int aliveCounter = 0;
        int newCarryOver1 = 0;
        int newCarryOver2 = 0;

        if (carryOver1 == -1 && carryOver2 == -1) {
            for (int j = 0; j < 3; j++) {
                for (int i = 0; i < 3; i++) {
                    int xB = x + (i - 1);
                    int yB = y + (j - 1);
                    Cell cell = map[xB][yB];
                    if (cell != null) {
                        if (j == 0) {
                            aliveCounter++;
                        } else if (j == 1) {
                            if (!(xB == x && yB == y)) {
                                aliveCounter++;
                            }
                            newCarryOver1++;
                        } else {
                            aliveCounter++;
                            if (i != 1) {
                                newCarryOver2++;
                            }
                        }
                    }
                }
            }
            return new int[]{aliveCounter, newCarryOver1, newCarryOver2};
        } else {
            int j = 2;
            newCarryOver1 = carryOver2;
            if (map[x][y] != null) {
                newCarryOver1++;
            }
            for (int i = 0; i < 3; i++) {
                int xB = x + (i - 1);
                int yB = y + (j - 1);
                Cell cell = map[xB][yB];
                if (cell != null) {
                    aliveCounter++;
                    if (i != 1) {
                        newCarryOver2++;
                    }
                }
            }
            return new int[]{aliveCounter + carryOver1 + carryOver2, newCarryOver1, newCarryOver2};
        }
    }
}
