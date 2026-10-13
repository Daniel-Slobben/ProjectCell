package slobben.cells.service.workers;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import slobben.cells.entities.Block;
import slobben.cells.util.BlockUtils;

import java.util.UUID;

class PackerTest {

    private static final long CELL_PACKS = 2_000_000_000;

    @ParameterizedTest
    @ValueSource(ints = {1000, 500, 1500, 2000})
    void packBlock(int blockSize) {
        // prepare
        Block block = new Block(0, 0, UUID.randomUUID(), blockSize);
        BlockUtils.setBlockToRandom(block, 6, blockSize);

        for (int i = 0; i < CELL_PACKS / ((long) blockSize * blockSize); i++) {
            Packer.packBlock(block);
        }
    }
}