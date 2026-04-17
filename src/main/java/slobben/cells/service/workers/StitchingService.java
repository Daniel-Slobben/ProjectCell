package slobben.cells.service.workers;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import slobben.cells.config.EnvironmentConfig;
import slobben.cells.entities.model.Block;
import slobben.cells.entities.model.BorderInfo;
import slobben.cells.service.ExecutorService;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static slobben.cells.util.BlockUtils.getKey;

@Service
@RequiredArgsConstructor
public class StitchingService implements Worker {

    private final EnvironmentConfig environmentConfig;
    private final ExecutorService executorService;

    private final Map<String, BorderInfo> bordersMap;
    private final Set<Block> blocks;

    @Value("${cells.size.blockSize}")
    private int blockSize;
    private int blockSizeWithBorder;

    @PostConstruct
    void init() {
        this.blockSizeWithBorder = environmentConfig.getBlockSizeWithBorder();
    }

    @Override
    public String getName() {
        return "Stitching blocks";
    }

    @Override
    public void execute() {
        Set<Runnable> tasks = blocks.stream().map(block -> (Runnable) () -> stitchBlock(block)).collect(Collectors.toSet());
        executorService.executeTasksParallel(tasks);
    }


    public void stitchBlock(Block block) {
        removeBorders(block.getCells());
        BorderInfo map = bordersMap.get(getKey(block.getX(), block.getY()));
        if (map != null && map.isHasAliveCells()) {
            map.copyCells(block);
        }
    }

    private void removeBorders(boolean[][] cells) {
        int max = blockSize + 1;

        // x keys
        cells[0] = new boolean[blockSizeWithBorder];
        cells[max] = new boolean[blockSizeWithBorder];

        // y keys
        for (int i = 0; i < cells[0].length; i++) {
            cells[i][0] = false;
            cells[i][max] = false;
        }
    }
}
