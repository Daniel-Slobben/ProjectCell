package slobben.cells.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import slobben.cells.config.EnvironmentConfig;
import slobben.cells.entities.Pattern;
import slobben.cells.entities.model.Block;
import slobben.cells.util.BlockUtils;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorldEditor {

    private final Map<String, Block> blocks;
    private final EnvironmentConfig environmentConfig;

    public void setCells(long startingX, long startingY, Pattern pattern) {
        int blockSize = environmentConfig.getBlockSize();

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

                Block block = blocks.get(BlockUtils.getKey(blockX, blockY));
                if (block == null) {
                    block = new Block(blockX, blockY, blockSize);
                    blocks.put(block.getKey(), block);
                }

                block.getCells()[relativeCellX + 1][relativeCellY + 1] = pattern.matrix()[x][y];
            }
        }
    }
}
