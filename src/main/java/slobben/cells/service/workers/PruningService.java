package slobben.cells.service.workers;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import slobben.cells.entities.model.Block;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class PruningService implements Worker {
    private final Set<Block> blocks;

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
            pruneBlocks(blocks);
        }
    }

    /**
     * Removes all blocks that don't have any living cells.
     * @param blocks -> the blocks to check for pruning
     */
    public void pruneBlocks(Set<Block> blocks) {
        blocks.removeIf(block -> !hasTrueValue(block));
    }

    private boolean hasTrueValue(Block block) {
        for (int x = 0; x < block.getCells().length; x++) {
            for (int y = 0; y < block.getCells().length; y++) {
                if (block.getCells()[x][y]) {
                    return true;
                }
            }
        }
        return false;
    }

}
