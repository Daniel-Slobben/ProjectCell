package slobben.cells.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import slobben.cells.config.EnvironmentConfig;
import slobben.cells.dto.internal.BlockUpdate;
import slobben.cells.util.BlockUtils;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorldEditor {

    private final Map<String, BlockUpdate> blockUpdates;
    private final EnvironmentConfig environmentConfig;
    private int blockSize = 0;

    @PostConstruct
    void init() {
        this.blockSize = environmentConfig.getBlockSize();
    }

    public void setCell(int x, int y, UUID id) {
        int blockX = x / blockSize;
        int blockY = y / blockSize;
        int relativeCellX = Math.toIntExact(x % blockSize);
        if (relativeCellX < 0) {
            relativeCellX = relativeCellX + blockSize;
        }
        int relativeCellY = Math.toIntExact(y % blockSize);
        if (relativeCellY < 0) {
            relativeCellY = relativeCellY + blockSize;
        }

        BlockUpdate block = blockUpdates.get(BlockUtils.getKey(blockX, blockY));
        if (block == null) {
            block = new BlockUpdate(blockX, blockY, new boolean[blockSize][blockSize], id);
            blockUpdates.put(block.getKey(), block);
        }
        block.state()[relativeCellX][relativeCellY] = true;
    }
}
