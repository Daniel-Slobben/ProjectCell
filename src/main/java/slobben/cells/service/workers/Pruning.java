package slobben.cells.service.workers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import slobben.cells.entities.Block;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class Pruning extends Worker {
    private final Map<String, Block> blocks;

    private int counter = 0;
    @Value("${cells.pruning-per-generation}")
    private int pruningPerGeneration;

    @Override
    public String getName() {
        return "Deleting empty blocks";
    }

    @Override
    public void execute() {
        counter++;
        if (counter == pruningPerGeneration) {
            log.info("Starting pruning");
            pruneBlocks(blocks);
            counter = 0;
        }
    }

    /**
     * Removes all blocks that don't have any living cells.
     * @param blocks -> the blocks to check for pruning
     */
    public void pruneBlocks(Map<String, Block> blocks) {
        blocks.values().removeIf(block -> !hasTrueValue(block));
    }

    // the borders count as well, they still make cells in this block come alive
    private boolean hasTrueValue(Block block) {
        for (long[] row : block.getPackedCells()) {
            for (long word : row) {
                if (word != 0) return true;
            }
        }
        return false;
    }

}
