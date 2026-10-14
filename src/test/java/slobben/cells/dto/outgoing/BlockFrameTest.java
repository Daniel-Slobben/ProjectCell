package slobben.cells.dto.outgoing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BlockFrameTest {

    @Test
    void writesTheDocumentedLayout() {
        byte[] frame = BlockFrame.write(List.of(new EncodedBlock(1, -2, 0x01020304, EncodedBlockType.BORDER, new byte[]{9, 8})));

        assertThat(frame).containsExactly(
                1, 0, 0, 0,                     // x = 1
                -2, -1, -1, -1,                 // y = -2
                1,                              // type BORDER
                4, 3, 2, 1,                     // generation, little-endian
                2, 0, 0, 0,                     // encodedCells length
                9, 8);                          // encodedCells
    }

    @Test
    void blocksFollowEachOther() {
        List<EncodedBlock> blocks = List.of(
                new EncodedBlock(-5, 7, 12, EncodedBlockType.FULL, new byte[]{1, 2, 3}),
                new EncodedBlock(0, 0, 0, EncodedBlockType.BORDER, new byte[0]),
                new EncodedBlock(Integer.MIN_VALUE, Integer.MAX_VALUE, 40_000, EncodedBlockType.FULL, new byte[1000]));

        byte[] frame = BlockFrame.write(blocks);

        assertThat(frame).hasSize(3 * BlockFrame.BLOCK_HEADER_BYTES + 3 + 1000);
        assertThat(BlockFrameReader.read(frame)).isEqualTo(blocks);
    }

    @Test
    void noBlocksIsAnEmptyMessage() {
        assertThat(BlockFrame.write(List.of())).isEmpty();
    }
}
