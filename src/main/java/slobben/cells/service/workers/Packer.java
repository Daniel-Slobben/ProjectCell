package slobben.cells.service.workers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import org.springframework.stereotype.Service;
import slobben.cells.dto.outgoing.EncodedBlock;
import slobben.cells.dto.outgoing.EncodedBlockType;
import slobben.cells.entities.Block;
import slobben.cells.enums.BlockState;
import slobben.cells.service.ExecutorService;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Slf4j
public class Packer implements Worker {
    private static final LZ4Compressor compressor = LZ4Factory.fastestInstance().fastCompressor();
    private static final VarHandle LONG_LE = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
    // Multiplying a long whose 8 bytes are each 0 or 1 by this moves byte k to bit 56 + k, without carries.
    private static final long GATHER_BITS = 0x0102040810204080L;
    private final Map<String, Block> blocks;
    private final ExecutorService executorService;

    public static EncodedBlock packBlock(Block block) {
        byte[] packed = getPacked(block.getCells());
        byte[] compressed = compressor.compress(packed);
        return new EncodedBlock(block.getX(), block.getY(), block.getGeneration(), Base64.getEncoder().encodeToString(compressed), EncodedBlockType.FULL.name());
    }

    @Override
    public String getName() {
        return "Caching Encoded Blocks";
    }

    private static EncodedBlock packBorders(Block block) {
        if (BlockState.NEW.equals(block.getBlockState())) {
            return packBlock(block);
        }
        byte[] packed = getPackedBorders(block.getCells());
        byte[] compressed = compressor.compress(packed);
        return new EncodedBlock(block.getX(), block.getY(), block.getGeneration(), Base64.getEncoder().encodeToString(compressed), EncodedBlockType.BORDER.name());
    }

    private static void setBit(byte[] packed, int i) {
        packed[i / 8] |= (byte) (1 << (i % 8));
    }

    private static byte[] getPackedBorders(boolean[][] cells) {
        final int min = 1;
        final int max = cells.length - 2;
        final int size = max - min + 1;
        final int totalBits = size * 4 - 4;

        int index = 0;

        byte[] packed = new byte[(totalBits + 7) / 8];

        for (int i = min; i <= max; i++) {
            if (cells[i][max]) setBit(packed, index);
            index++;
        }
        for (int i = min; i <= max; i++) {
            if (cells[i][min]) setBit(packed, index);
            index++;
        }
        for (int i = min + 1; i < max; i++) {
            if (cells[min][i]) setBit(packed, index);
            index++;
        }
        for (int i = min + 1; i < max; i++) {
            if (cells[max][i]) setBit(packed, index);
            index++;
        }
        if (index != totalBits) {
            throw new IllegalStateException("index=" + index + " totalBits=" + totalBits + " size=" + size + " cells=" + cells.length);
        }
        return packed;
    }

    private static byte[] getPacked(boolean[][] cells) {
        final int size = cells.length - 2;
        final int totalBits = size * size;
        final int wholeWordCells = size & ~63;
        final int tailCells = size & 63;
        byte[] packed = new byte[(totalBits + 7) / 8];
        byte[] row = new byte[size + 64];

        // Rows are not word aligned in the output, so bits are streamed out 64 at a time via pending.
        long pending = 0;
        int pendingBits = 0;
        int pos = 0;
        for (int x = 1; x <= size; x++) {
            boolean[] cellRow = cells[x];
            for (int y = 0; y < size; y++) {
                row[y] = (byte) (cellRow[y + 1] ? 1 : 0);
            }
            for (int y = 0; y < wholeWordCells; y += 64) {
                long bits = pack64(row, y);
                LONG_LE.set(packed, pos, pending | bits << pendingBits);
                pos += 8;
                // bits >>> (64 - pendingBits), but 0 instead of bits when pendingBits == 0
                pending = (bits >>> 1) >>> (63 - pendingBits);
            }
            if (tailCells > 0) {
                long bits = pack64(row, wholeWordCells);
                pending |= bits << pendingBits;
                pendingBits += tailCells;
                if (pendingBits >= 64) {
                    LONG_LE.set(packed, pos, pending);
                    pos += 8;
                    pendingBits -= 64;
                    pending = bits >>> (tailCells - pendingBits);
                }
            }
        }
        for (; pos < packed.length; pos++, pending >>>= 8) {
            packed[pos] = (byte) pending;
        }
        return packed;
    }

    // Bit i of the result is row[y + i].
    private static long pack64(byte[] row, int y) {
        return pack8(row, y)
                | pack8(row, y + 8) << 8
                | pack8(row, y + 16) << 16
                | pack8(row, y + 24) << 24
                | pack8(row, y + 32) << 32
                | pack8(row, y + 40) << 40
                | pack8(row, y + 48) << 48
                | pack8(row, y + 56) << 56;
    }

    private static long pack8(byte[] row, int y) {
        return ((long) LONG_LE.get(row, y) * GATHER_BITS) >>> 56;
    }

    @Override
    public void execute() {
        Set<Runnable> tasks = blocks.values().stream().map(block -> (Runnable) () -> {
            block.setEncodedBlock(packBlock(block));
            block.setEncodedBlockBorders(packBorders(block));
        }).collect(Collectors.toSet());

        executorService.executeTasksParallel(tasks, getName());
    }
}



