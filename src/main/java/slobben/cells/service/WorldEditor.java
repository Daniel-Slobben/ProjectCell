package slobben.cells.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import slobben.cells.config.EnvironmentConfig;
import slobben.cells.dto.internal.BlockUpdate;
import slobben.cells.util.BlockCoordinatesResult;
import slobben.cells.util.BlockUtils;

import java.util.Map;
import java.util.UUID;

import static slobben.cells.util.Utils.getBlockCoordinates;

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
        BlockCoordinatesResult result = getBlockCoordinates(x, y, blockSize);

        BlockUpdate block = blockUpdates.get(BlockUtils.getKey(result.blockX(), result.blockY()));
        if (block == null) {
            block = new BlockUpdate(result.blockX(), result.blockY(), new boolean[blockSize][blockSize], id);
            blockUpdates.put(block.getKey(), block);
        }
        block.state()[result.relativeCellX()][result.relativeCellY()] = true;
    }
}
