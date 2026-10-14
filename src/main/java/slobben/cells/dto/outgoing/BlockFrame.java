package slobben.cells.dto.outgoing;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/**
 * Binary websocket message with blocks, one block after the other until the end of the message.
 * Per block, little-endian:
 * <pre>
 * offset  type  field
 *  0      i32   x
 *  4      i32   y
 *  8      u8    type (0 = FULL, 1 = BORDER)
 *  9      u32   generation
 * 13      u32   encodedCells length
 * 17      ...   encodedCells (LZ4 block, see EncodedBlock)
 * </pre>
 */
public final class BlockFrame {

    public static final int BLOCK_HEADER_BYTES = 17;

    private BlockFrame() {
    }

    public static byte[] write(List<EncodedBlock> blocks) {
        int size = 0;
        for (EncodedBlock block : blocks) {
            size += BLOCK_HEADER_BYTES + block.encodedCells().length;
        }
        ByteBuffer buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        for (EncodedBlock block : blocks) {
            buffer.putInt(block.x())
                    .putInt(block.y())
                    .put((byte) block.type().getCode())
                    .putInt(block.generation())
                    .putInt(block.encodedCells().length)
                    .put(block.encodedCells());
        }
        return buffer.array();
    }
}
