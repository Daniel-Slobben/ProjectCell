package slobben.cells.service.worker;

import org.junit.jupiter.api.Test;
import slobben.cells.entities.Block;
import slobben.cells.service.workers.Pruning;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PruningTest {

    private final Map<String, Block> blocks = new HashMap<>();
    private final Pruning pruning = new Pruning(blocks);

    @Test
    void prunesABlockThatIsEmptyAtTwoPruningsInARow() {
        // prepare
        Block empty = block(0, 0, false);
        Block revived = block(1, 0, false);
        Block alive = block(2, 0, true);

        // execute
        pruning.pruneBlocks(blocks);
        Map<String, Block> afterFirst = Map.copyOf(blocks);
        revived.getPackedCells()[1][0] = 1;
        pruning.pruneBlocks(blocks);

        // verify
        assertThat(afterFirst).containsOnlyKeys(empty.getKey(), revived.getKey(), alive.getKey());
        assertThat(blocks).containsOnlyKeys(revived.getKey(), alive.getKey());
    }

    @Test
    void prunesABlockThatDiesAgainOnlyAtTheNextPruning() {
        // prepare
        Block block = block(0, 0, false);
        pruning.pruneBlocks(blocks);
        block.getPackedCells()[1][0] = 1;
        pruning.pruneBlocks(blocks);
        block.getPackedCells()[1][0] = 0;

        // execute
        pruning.pruneBlocks(blocks);
        Map<String, Block> afterDying = Map.copyOf(blocks);
        pruning.pruneBlocks(blocks);

        // verify
        assertThat(afterDying).containsOnlyKeys(block.getKey());
        assertThat(blocks).isEmpty();
    }

    private Block block(int x, int y, boolean alive) {
        Block block = new Block(x, y, UUID.randomUUID(), 20);
        if (alive) block.getPackedCells()[1][0] = 1;
        blocks.put(block.getKey(), block);
        return block;
    }
}
