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
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class Packer implements Worker {
    private static final LZ4Compressor compressor = LZ4Factory.fastestInstance().fastCompressor();

    private static final VarHandle LONG_LE = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
    private final Map<String, Block> blocks;
    private final ExecutorService executorService;

    public static void packAll(Block block) {
        boolean[][] cells = block.getCells();
        int size = cells.length - 2;
        long[][] rows = snapshotRows(block.getPackedCells(), size);

        Supplier<byte[]> full = () -> getPacked(rows, size);
        Supplier<byte[]> borders = () -> getPackedBorders(rows, size);

        LazyEncodedBlock lazyFull = new LazyEncodedBlock(block, full, EncodedBlockType.FULL);
        block.setEncodedBlock(lazyFull);
        block.setEncodedBlockBorders(BlockState.NEW.equals(block.getBlockState())
                ? lazyFull
                : new LazyEncodedBlock(block, borders, EncodedBlockType.BORDER));
    }

    @Override
    public String getName() {
        return "Caching Encoded Blocks";
    }

    /**
     * Copies the rows that have live inner cells, empty rows stay null. The GoL changes its rows in place next tick,
     * so the copy is what keeps the encodings at this tick. Mostly empty blocks copy next to nothing.
     */
    private static long[][] snapshotRows(long[][] packedCells, int size) {
        long[][] rows = new long[size + 2][];
        int lastWord = ((size + 1) >>> 6) + 1;
        // per word: the bits of columns 1 .. size, so the border columns 0 and size + 1 don't count
        long[] innerMask = new long[lastWord + 1];
        for (int y = 1; y <= size; y++) {
            innerMask[(y >>> 6) + 1] |= 1L << y;
        }
        for (int x = 1; x <= size; x++) {
            long[] row = packedCells[x];
            long any = 0;
            for (int i = 1; i <= lastWord; i++) {
                any |= row[i] & innerMask[i];
            }
            if (any != 0) rows[x] = row.clone();
        }
        return rows;
    }

    private static final class LazyEncodedBlock implements Supplier<EncodedBlock> {
        private final int x;
        private final int y;
        private final int generation;
        private final EncodedBlockType type;
        private Supplier<byte[]> packed;
        private EncodedBlock encoded;

        LazyEncodedBlock(Block block, Supplier<byte[]> packed, EncodedBlockType type) {
            this.x = block.getX();
            this.y = block.getY();
            this.generation = block.getGeneration();
            this.type = type;
            this.packed = packed;
        }

        @Override
        public synchronized EncodedBlock get() {
            if (encoded == null) {
                encoded = new EncodedBlock(x, y, generation, type, compressor.compress(packed.get()));
                packed = null;
            }
            return encoded;
        }
    }

    private static void setBit(byte[] packed, int i) {
        packed[i / 8] |= (byte) (1 << (i % 8));
    }

    private static byte[] getPackedBorders(long[][] rows, int size) {
        byte[] packed = new byte[(size * 4 - 4 + 7) / 8];
        int index = 0;
        for (int i = 1; i <= size; i++) {
            if (bit(rows[i], size)) setBit(packed, index);
            index++;
        }
        for (int i = 1; i <= size; i++) {
            if (bit(rows[i], 1)) setBit(packed, index);
            index++;
        }
        for (int i = 2; i < size; i++) {
            if (bit(rows[1], i)) setBit(packed, index);
            index++;
        }
        for (int i = 2; i < size; i++) {
            if (bit(rows[size], i)) setBit(packed, index);
            index++;
        }
        return packed;
    }

    private static boolean bit(long[] row, int column) {
        return row != null && (row[(column >>> 6) + 1] >>> column & 1) != 0;
    }

    private static byte[] getPacked(long[][] packedCells, int size) {
        final int wholeWords = size >>> 6;
        final long tailMask = (1L << (size & 63)) - 1;
        final long[] emptyRow = new long[wholeWords + 3];
        BitStream out = new BitStream(size);

        for (int x = 1; x <= size; x++) {
            long[] row = packedCells[x] == null ? emptyRow : packedCells[x];
            for (int i = 1; i <= wholeWords; i++) {
                out.write64(row[i] >>> 1 | row[i + 1] << 63);
            }
            // masks off the right border column
            out.writeTail((row[wholeWords + 1] >>> 1 | row[wholeWords + 2] << 63) & tailMask);
        }
        return out.finish();
    }

    private static final class BitStream {
        private final byte[] packed;
        private final int tailCells;
        private long pending = 0;
        private int pendingBits = 0;
        private int pos = 0;

        BitStream(int size) {
            this.packed = new byte[(size * size + 7) / 8];
            this.tailCells = size & 63;
        }

        void write64(long bits) {
            LONG_LE.set(packed, pos, pending | bits << pendingBits);
            pos += 8;
            // bits >>> (64 - pendingBits), but 0 instead of bits when pendingBits == 0
            pending = (bits >>> 1) >>> (63 - pendingBits);
        }

        // bits holds the last tailCells cells of a row, the higher bits are 0
        void writeTail(long bits) {
            if (tailCells == 0) return;
            pending |= bits << pendingBits;
            pendingBits += tailCells;
            if (pendingBits >= 64) {
                LONG_LE.set(packed, pos, pending);
                pos += 8;
                pendingBits -= 64;
                pending = bits >>> (tailCells - pendingBits);
            }
        }

        byte[] finish() {
            for (; pos < packed.length; pos++, pending >>>= 8) {
                packed[pos] = (byte) pending;
            }
            return packed;
        }
    }

    @Override
    public void execute() {
        Set<Runnable> tasks = blocks.values().stream().map(block -> (Runnable) () -> packAll(block)).collect(Collectors.toSet());

        executorService.executeTasksParallel(tasks, getName());
    }
}
