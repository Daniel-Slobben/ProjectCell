package slobben.cells.dto.outgoing;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Reference reader for BlockFrame, written straight from the layout (what a client does).
 */
public final class BlockFrameReader {

    private BlockFrameReader() {
    }

    public static List<EncodedBlock> read(byte[] frame) {
        ByteBuffer buffer = ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN);
        List<EncodedBlock> blocks = new ArrayList<>();
        while (buffer.hasRemaining()) {
            int x = buffer.getInt();
            int y = buffer.getInt();
            int typeCode = Byte.toUnsignedInt(buffer.get());
            EncodedBlockType type = Arrays.stream(EncodedBlockType.values())
                    .filter(t -> t.getCode() == typeCode)
                    .findFirst().orElseThrow();
            int generation = buffer.getInt();
            byte[] payload = new byte[buffer.getInt()];
            buffer.get(payload);
            blocks.add(new EncodedBlock(x, y, generation, type, payload));
        }
        return blocks;
    }
}
