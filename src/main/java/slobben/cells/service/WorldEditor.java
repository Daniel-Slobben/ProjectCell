package slobben.cells.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import slobben.cells.config.EnvironmentConfig;
import slobben.cells.dto.BlockUpdate;
import slobben.cells.entities.Pattern;
import slobben.cells.service.workers.chaos.ChaosHit;
import slobben.cells.util.BlockUtils;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorldEditor {

    private final Map<String, BlockUpdate> blockUpdates;
    private final EnvironmentConfig environmentConfig;

    public void setCells(long startingX, long startingY, Pattern pattern) {
        // TODO: Bit ugly no?
        setCells(startingX, startingY, new ChaosHit((int) startingX, (int) startingY, null, pattern));
    }

    public void setCells(long startingX, long startingY, ChaosHit responsibleChaosHit) {
        int blockSize = environmentConfig.getBlockSize();
        Pattern pattern = responsibleChaosHit.getPattern();

        for (int x = 0; x < pattern.x(); x++) {
            int blockX = Math.toIntExact((startingX + x) / blockSize);
            int relativeCellX = Math.toIntExact((startingX + x) % blockSize);
            if (relativeCellX < 0) {
                blockX--;
                relativeCellX = relativeCellX + blockSize;
            }

            for (int y = 0; y < pattern.y(); y++) {
                int blockY = Math.toIntExact((startingY + y) / blockSize);
                int relativeCellY = Math.toIntExact((startingY + y) % blockSize);
                if (relativeCellY < 0) {
                    blockY--;
                    relativeCellY = relativeCellY + blockSize;
                }

                BlockUpdate block = blockUpdates.get(BlockUtils.getKey(blockX, blockY));
                if (block == null) {
                    block = new BlockUpdate(blockX, blockY, new boolean[blockSize][blockSize], responsibleChaosHit);
                    blockUpdates.put(block.getKey(), block);
                }

                block.state()[relativeCellX][relativeCellY] = pattern.matrix()[x][y];
            }
        }
    }
}
