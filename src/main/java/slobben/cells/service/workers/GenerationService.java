package slobben.cells.service.workers;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import slobben.cells.config.EnvironmentConfig;
import slobben.cells.entities.model.Block;
import slobben.cells.service.ExecutorService;

import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GenerationService implements Worker {

    private final EnvironmentConfig environmentConfig;
    private final ExecutorService executorService;
    private final Set<Block> blocks;

    private int blockSizeWithBorder;
    @Value("${cells.size.blockSize}")
    private int blockSize;

    @Override
    public String getName() {
        return "Generation";
    }

    @PostConstruct
    void init() {
        this.blockSizeWithBorder = environmentConfig.getBlockSizeWithBorder();
    }

    @Override
    public void execute() {
        Set<Runnable> tasks = blocks.stream().map(block -> ((Runnable) () -> setNextState(block))).collect(Collectors.toSet());
        executorService.executeTasksParallel(tasks);
    }

    public void setNextState(Block block) {
        byte[][] heatmap = new byte[blockSizeWithBorder][blockSizeWithBorder];
        for (int x = 0; x < blockSizeWithBorder; x++) {
            for (int y = 0; y < blockSizeWithBorder; y++) {
                if (!block.getCells()[x][y]) continue;
                for (int i = -1; i <= 1; i++) {
                    for (int j = -1; j <= 1; j++) {
                        if (i == 0 && j == 0) continue;
                        if (x + i < 0 || y + j < 0 || x + i >= blockSizeWithBorder || y + j >= blockSizeWithBorder) continue;
                        heatmap[x + i][y + j]++;
                    }
                }
            }
        }

        for (int x = 1; x < blockSize + 1; x++) {
            for (int y = 1; y < blockSize + 1; y++) {
                // If cell was dead
                if (!block.getCells()[x][y]) {
                    if (heatmap[x][y] == 3) {
                        block.getCells()[x][y] = true;
                    }
                }
                // If cell was alive
                else {
                    if (!(heatmap[x][y] == 2 || heatmap[x][y] == 3)) {
                        block.getCells()[x][y] = false;
                    }
                }
            }
        }
    }
}
