package slobben.cells.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import slobben.cells.config.EnvironmentConfig;
import slobben.cells.entities.Block;
import slobben.cells.util.BlockCoordinatesResult;
import slobben.cells.util.BlockUtils;

import java.util.Map;
import java.util.UUID;

import static slobben.cells.util.Utils.getBlockCoordinates;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorldEditor {

    private final Map<String, Block> newBlocks;
    private final EnvironmentConfig environmentConfig;
    private int blockSize = 0;

    @PostConstruct
    void init() {
        this.blockSize = environmentConfig.getBlockSize();
    }

    public void setCell(int x, int y, UUID id) {
        BlockCoordinatesResult result = getBlockCoordinates(x, y, blockSize);

        Block block = newBlocks.get(BlockUtils.getKey(result.blockX(), result.blockY()));
        if (block == null) {
            block = new Block(result.blockX(), result.blockY(), id, blockSize);
            newBlocks.put(block.getKey(), block);
        }
        block.getCells()[result.relativeCellX() + 1][result.relativeCellY() + 1] = true;
    }
}
