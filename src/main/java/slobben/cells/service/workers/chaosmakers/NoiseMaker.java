package slobben.cells.service.workers.chaosmakers;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import slobben.cells.dto.internal.Coordinates;
import slobben.cells.entities.Block;
import slobben.cells.entities.ChaosHit;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Random cells over a square of blocks, so a client at the farthest zoom has every block in view alive.
 * For local testing only: it's never picked at random, only with cells.chaos.type NOISE.
 */
@Component
@RequiredArgsConstructor
public class NoiseMaker implements Maker {
    private final Map<String, Block> newBlocks;
    @Value("${cells.size.blockSize}")
    private int blockSize;

    @Override
    public ChaosHit getChaosHit(int worldX, int worldY, int size) {
        UUID chaosHitId = UUID.randomUUID();
        int blocks = Math.max(1, size / blockSize);
        int startX = Math.floorDiv(worldX - size / 2, blockSize);
        int startY = Math.floorDiv(worldY - size / 2, blockSize);
        for (int x = startX; x < startX + blocks; x++) {
            for (int y = startY; y < startY + blocks; y++) {
                Block block = new Block(x, y, chaosHitId, blockSize);
                fill(block.getCells());
                newBlocks.put(block.getKey(), block);
            }
        }
        return new ChaosHit("Noise of " + blocks + " x " + blocks + " blocks", chaosHitId, new Coordinates(worldX, worldY),
                100_000, size);
    }

    // a third of the cells alive
    private void fill(boolean[][] cells) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 1; i <= blockSize; i++) {
            for (int j = 1; j <= blockSize; j++) {
                cells[i][j] = random.nextInt(3) == 0;
            }
        }
    }
}
