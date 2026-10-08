package slobben.cells.service.workers;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import slobben.cells.entities.Block;
import slobben.cells.service.ExecutorService;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StitchingService implements Worker {

    private final ExecutorService executorService;

    private final Map<String, Block> blocks;

    @Value("${cells.size.blockSize}")
    private int blockSize;

    @Override
    public String getName() {
        return "Stitching blocks";
    }

    @Override
    public void execute() {
        Set<Runnable> tasks = blocks.values().stream().map(block -> (Runnable) () -> stitchBlock(block)).collect(Collectors.toSet());
        executorService.executeTasksParallel(tasks, getName());
    }

    public void stitchBlock(Block block) {
        block.getCells()[0][0] = block.isLowXlowYcorner();
        block.getCells()[0][blockSize + 1] = block.isLowXhighYcorner();
        block.getCells()[blockSize + 1][0] = block.isHighXlowYcorner();
        block.getCells()[blockSize + 1][blockSize + 1] = block.isHighXhighYcorner();

        System.arraycopy(block.getLowXBorder(), 0, block.getCells()[0], 1, blockSize);
        System.arraycopy(block.getHighXBorder(), 0, block.getCells()[blockSize + 1], 1, blockSize);

        for (int i = 0; i < blockSize; i++) {
            block.getCells()[i + 1][0] = block.getLowYBorder()[i];
            block.getCells()[i + 1][blockSize + 1] = block.getHighYBorder()[i];
        }
    }
}
