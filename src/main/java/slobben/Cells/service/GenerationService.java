package slobben.Cells.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import slobben.Cells.entities.model.Block;
import slobben.Cells.enums.CellState;
import slobben.Cells.enums.Direction;

import java.util.stream.Stream;

import static slobben.Cells.enums.CellState.ALIVE;
import static slobben.Cells.enums.CellState.DEAD;

@Service
@Slf4j
@RequiredArgsConstructor
public class GenerationService {

    private final EnvironmentService environmentService;
    private int blockSize;
    private int blockSizeWithBorder;

    @PostConstruct
    public void init() {
        blockSizeWithBorder = environmentService.getBlockSizeWithBorder();
        blockSize = environmentService.getBlockSize();
    }

    @SneakyThrows
    public void setNextState(Block block) {
        byte[][] heatmap = new byte[blockSizeWithBorder][blockSizeWithBorder];
        byte[][] newColors = new byte[blockSizeWithBorder][blockSizeWithBorder];
        for (int x = 0; x < blockSizeWithBorder; x++) {
            for (int y = 0; y < blockSizeWithBorder; y++) {
                // if alive
                byte sourceColor = block.getCells()[x][y];
                if (0 != sourceColor) continue;
                for (int i = -1; i <= 1; i++) {
                    for (int j = -1; j <= 1; j++) {
                        if (i == 0 && j == 0) continue;
                        if (x + i < 0 || y + j < 0 || x + i >= blockSizeWithBorder || y + j >= blockSizeWithBorder) continue;
                        heatmap[x + i][y + j]++;
                        newColors[x + i][y + j] = sourceColor;
                    }
                }
            }
        }
        for (int x = 1; x < blockSize + 1; x++) {
            for (int y = 1; y < blockSize + 1; y++) {
                // If cell was dead
                if (0 == block.getCells()[x][y]) {
                    if (applyConwayGameOfLifeRules(DEAD, heatmap[x][y]).equals(ALIVE)) {
                        block.getCells()[x][y] = newColors[x][y];
                    }
                }
                // If cell was alive
                else {
                    if (applyConwayGameOfLifeRules(ALIVE, heatmap[x][y]).equals(DEAD)) {
                        block.getCells()[x][y] = 0;
                    }
                }
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
}
