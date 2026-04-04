package slobben.cells.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import slobben.cells.entities.model.Block;

import java.util.Set;

@Service
@Slf4j
public class PruningService {

    /**
     * Removes all blocks that don't have any living cells.
     * @param blocks -> the blocks to check for pruning
     */
    public void pruneBlocks(Set<Block> blocks) {
        long timer = System.currentTimeMillis();
        blocks.removeIf(block -> !hasTrueValue(block));
        log.info("Pruning took {}ms", System.currentTimeMillis() - timer);
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
