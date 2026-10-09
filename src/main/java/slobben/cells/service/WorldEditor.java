package slobben.cells.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import slobben.cells.entities.Block;
import slobben.cells.util.BlockUtils;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorldEditor {

    private final Map<String, Block> newBlocks;
    @Value("${cells.size.blockSize}")
    private int blockSize;

    public void setCell(int x, int y, UUID id) {
        int blockX = Math.floorDiv(x, blockSize);
        int blockY = Math.floorDiv(y, blockSize);
        int relativeCellX = Math.floorMod(x, blockSize);
        if (relativeCellX < 0) {
            relativeCellX = relativeCellX + blockSize;
        }
        int relativeCellY = Math.floorMod(y, blockSize);
        if (relativeCellY < 0) {
            relativeCellY = relativeCellY + blockSize;
        }

        Block block = newBlocks.get(BlockUtils.getKey(blockX, blockY));
        if (block == null) {
            block = new Block(blockX, blockY, id, blockSize);
            newBlocks.put(block.getKey(), block);
        }
        block.getCells()[relativeCellX + 1][relativeCellY + 1] = true;
    }
}
