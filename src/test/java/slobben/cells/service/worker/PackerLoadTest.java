package slobben.cells.service.worker;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import slobben.cells.entities.Block;
import slobben.cells.service.workers.Packer;

import java.util.UUID;

import static slobben.cells.util.BlockUtils.setBlockToRandom;

class PackerLoadTest {

    private static final long CELL_PACKS = 2_000_000_000;

    @ParameterizedTest
    @Disabled("Too expensive for normal test run")
    @ValueSource(ints = {500, 1000, 1500, 2000})
    void profilePacker(int blockSize) {
        Block block = new Block(0, 0, UUID.randomUUID(), blockSize);
        setBlockToRandom(block, 6, blockSize);
        AlgorithmGolLoadTest.algorithmGol(blockSize, 50).setNextState(block);

        for (int i = 0; i < CELL_PACKS / ((long) blockSize * blockSize); i++) {
            Packer.packAll(block);
            block.getEncodedBlock();
            block.getEncodedBlockBorders();
        }
    }
}
