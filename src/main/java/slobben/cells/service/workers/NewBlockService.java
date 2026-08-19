package slobben.cells.service.workers;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import slobben.cells.config.EnvironmentConfig;
import slobben.cells.dto.internal.BlockUpdate;
import slobben.cells.entities.model.Block;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NewBlockService implements Worker {

    private final EnvironmentConfig environmentConfig;
    private final Map<String, BlockUpdate> blockUpdates;
    private final Map<String, Block> blocks;

    @Override
    public String getName() {
        return "Adding blockupdates to blocks";
    }

    @Override
    public void execute() {
        checkForExternalBlockUpdates();
    }

    private void checkForExternalBlockUpdates() {
        for (BlockUpdate blockUpdate : blockUpdates.values()) {
            Optional<Block> optionalBlock = blocks.values().stream().filter(block -> block.getX() == blockUpdate.x() && block.getY() == blockUpdate.y()).findFirst();
            if (optionalBlock.isPresent()) {
                updateBlock(optionalBlock.get(), blockUpdate);
            } else {
                createBlock(blockUpdate);
            }
        }
        blockUpdates.clear();
    }

    private void updateBlock(Block block, BlockUpdate update) {
        int blockSize = environmentConfig.getBlockSize();
        for (int x = 1; x < blockSize + 1; x++) {
            System.arraycopy(update.state()[x - 1], 0, block.getCells()[x], 1, blockSize);
        }
    }

    private void createBlock(BlockUpdate blockUpdate) {
        boolean[][] matrix = new boolean[environmentConfig.getBlockSizeWithBorder()][environmentConfig.getBlockSizeWithBorder()];

        Block newBlock = new Block(blockUpdate.x(), blockUpdate.y(), blockUpdate.responsibleChaosHit(), matrix);
        updateBlock(newBlock, blockUpdate);
        blocks.put(newBlock.getKey(), newBlock);
    }

}
