package slobben.Cells.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import slobben.Cells.entities.model.Block;
import slobben.Cells.enums.CellState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    public void setNextStateNew(Block block) {
        int blockSizeWithBorder = environmentService.getBlockSizeWithBorder();
        ArrayList<Integer>[][] colorNeighbourMap = new ArrayList[blockSizeWithBorder][blockSizeWithBorder];
        boolean[][] aliveMap = new boolean[blockSizeWithBorder][blockSizeWithBorder];

// Initialize all ArrayLists first
        for (int i = 0; i < blockSizeWithBorder; i++) {
            for (int j = 0; j < blockSizeWithBorder; j++) {
                colorNeighbourMap[i][j] = new ArrayList<>();
            }
        }

// Then your simplified loop
        block.getCells().forEach((x, yRow) -> yRow.forEach((y, cell) -> {
            aliveMap[x][y] = true;
            if (!(x == 0 || x == blockSizeWithBorder - 1 || y == 0 || y == blockSizeWithBorder - 1)) {
                for (int i = -1; i <= 1; i++) {
                    for (int j = -1; j <= 1; j++) {
                        if (i == 0 && j == 0) continue;
                        int neighborX = x + i;
                        int neighborY = y + j;
                        if (neighborX >= 0 && neighborX < blockSizeWithBorder && neighborY >= 0 && neighborY < blockSizeWithBorder) {
                            colorNeighbourMap[neighborX][neighborY].add(cell);
                        }
                    }
                }
            }
        }));
        for (int x = 1; x < blockSize + 1; x++) {
            for (int y = 1; y < blockSize + 1; y++) {
                int aliveNeighbours = 0;
                if (colorNeighbourMap[x][y] != null) {
                    aliveNeighbours = colorNeighbourMap[x][y].size();
                }
                // If cell was dead
                if (!aliveMap[x][y]) {
                    if (applyConwayGameOfLifeRules(DEAD, aliveNeighbours).equals(ALIVE)) {
                        Integer newColor = getColorMode(colorNeighbourMap[x][y]);
                        insertCell(block.getCells(), newColor, x, y);
                    }
                }
                // If cell was alive
                else {
                    if (applyConwayGameOfLifeRules(ALIVE, aliveNeighbours).equals(DEAD)) {
                        removeCell(block.getCells(), x, y);
                    }
                }
            }
        }
    }

    private Integer getColorMode(ArrayList<Integer> colors) {
        Optional<Map.Entry<Integer, Long>> modeColor = colors.stream().collect(Collectors.groupingBy(Function.identity(), Collectors.counting())).entrySet().stream().max(Map.Entry.comparingByValue());
        return modeColor.get().getKey();
    }

    private void insertCell(Map<Integer, Map<Integer, Integer>> cellMap, Integer cell, int x, int y) {
        cellMap.computeIfAbsent(x, value -> new HashMap<>()).put(y, cell);
    }

    private void removeCell(Map<Integer, Map<Integer, Integer>> cellMap, int x, int y) {
        Map<Integer, Integer> row = cellMap.get(x);
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

    private int[] getAliveNeighbourCount(int x, int y, Integer[][] map, int carryOver1, int carryOver2) {
        int aliveCounter = 0;
        int newCarryOver1 = 0;
        int newCarryOver2 = 0;

        if (carryOver1 == -1 && carryOver2 == -1) {
            for (int j = 0; j < 3; j++) {
                for (int i = 0; i < 3; i++) {
                    int xB = x + (i - 1);
                    int yB = y + (j - 1);
                    Integer cell = map[xB][yB];
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
                Integer cell = map[xB][yB];
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
