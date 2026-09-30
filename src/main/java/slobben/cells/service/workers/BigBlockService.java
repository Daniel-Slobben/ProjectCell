package slobben.cells.service.workers;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import slobben.cells.entities.Coordinates;
import slobben.cells.entities.model.Block;
import slobben.cells.util.BlockUtils;

import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class BigBlockService implements Worker {

    private final UUID responsibleHitId = UUID.randomUUID();

    private final Map<String, Block> blocks;
    private final Map<String, Block> bigBlocks;

    @Value("${cells.size.bigBlockCombineFactor:4}")
    private int bigBlockFactor;

    @Value("${cells.size.blockSize}")
    private int blockSize;

    @Override
    public void execute() {
        blocks.values().forEach(block -> {
            String bigKey = BlockUtils.keyToBigKey(block.getKey(), bigBlockFactor);
            Block bigBlock = bigBlocks.get(bigKey);
            if (bigBlock == null) {
                Coordinates coordinates = BlockUtils.resolveKey(bigKey);
                bigBlock = new Block(coordinates.x(), coordinates.y(), responsibleHitId, blockSize);
                bigBlocks.put(bigKey, bigBlock);
            }

            block.setBigCornerCells(bigBlock.getCells(), BlockUtils.keyToBigKeyCorner(block.getKey(), bigBlockFactor), bigBlockFactor);
        });
    }

    @Override
    public String getName() {
        return "Big Block Service";
    }
}
