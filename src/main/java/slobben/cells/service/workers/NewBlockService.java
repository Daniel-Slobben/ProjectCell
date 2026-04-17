package slobben.cells.service.workers;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import slobben.cells.config.EnvironmentConfig;
import slobben.cells.dto.BlockUpdate;
import slobben.cells.entities.model.Block;
import slobben.cells.util.BlockUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class NewBlockService implements Worker {

    private final EnvironmentConfig environmentConfig;
    private final List<BlockUpdate> blockUpdates;
    private final Set<Block> blocks;
    private final Map<String, Block> ghostBlocks;

    public void setBlock(int x, int y, boolean[][] body) {
        blockUpdates.add(BlockUpdate.builder().x(x).y(y).state(body).build());
    }

    @Override
    public String getName() {
        return "Adding blockupdates to blocks";
    }

    @Override
    public void execute() {
        checkForExternalBlockUpdates();
    }

    private void checkForExternalBlockUpdates() {
        for (BlockUpdate blockUpdate : blockUpdates) {
            Optional<Block> optionalBlock = blocks.stream().filter(block -> block.getX() == blockUpdate.x() && block.getY() == blockUpdate.y()).findFirst();
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

        Block newBlock;
        var key = BlockUtils.getKey(blockUpdate.x(), blockUpdate.y());
        if (ghostBlocks.containsKey(key)) {
            var ghostBlock = ghostBlocks.get(key);
            ghostBlock.setGhostBlock(false);
            ghostBlocks.remove(key);
            newBlock = ghostBlock;
        } else {
            newBlock = new Block(blockUpdate.x(), blockUpdate.y(), matrix);
        }

        updateBlock(newBlock, blockUpdate);
        blocks.add(newBlock);
    }

}
