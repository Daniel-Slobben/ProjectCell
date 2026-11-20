package slobben.cells.service.workers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import slobben.cells.entities.Block;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class Pruning extends Worker {
    private final Map<String, Block> blocks;

    private int counter = 0;
    @Value("${cells.pruning-per-generation}")
    private int pruningPerGeneration;
    private Set<Block> emptyBefore = new HashSet<>();

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

    // empty at two prunings in a row, so every client watching sectors got them empty
    public void pruneBlocks(Map<String, Block> blocks) {
        Set<Block> empty = blocks.values().stream().filter(block -> !hasTrueValue(block)).collect(Collectors.toSet());
        blocks.values().removeIf(block -> empty.contains(block) && emptyBefore.contains(block));
        empty.removeAll(emptyBefore);
        emptyBefore = empty;
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
