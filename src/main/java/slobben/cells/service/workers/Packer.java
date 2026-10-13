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
import java.util.function.Supplier;
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
        boolean[][] cells = block.getCells();
        byte[] packed = hasGolRows(block) ? getPacked(block.getPackedCells(), cells.length - 2) : getPacked(cells);
        return encode(block.getX(), block.getY(), block.getGeneration(), packed, EncodedBlockType.FULL);
    }

    /**
     * Sets both the full and the border encoding of the block. They are only sent for blocks a client watches, so
     * here only a snapshot of this tick's cells is taken: packing and compressing happen when a client first asks.
     */
    public static void packAll(Block block) {
        boolean[][] cells = block.getCells();
        int size = cells.length - 2;
        Supplier<byte[]> full;
        Supplier<byte[]> borders;
        if (hasGolRows(block)) {
            long[][] rows = snapshotRows(block.getPackedCells(), size);
            full = () -> getPacked(rows, size);
            borders = () -> getPackedBorders(rows, size);
        } else {
            // no bit rows to snapshot, so pack now: the cells can change before a client asks
            byte[] packed = getPacked(cells);
            byte[] packedBorders = getPackedBorders(cells);
            full = () -> packed;
            borders = () -> packedBorders;
        }
        LazyEncodedBlock lazyFull = new LazyEncodedBlock(block, full, EncodedBlockType.FULL);
        block.setEncodedBlock(lazyFull);
        // a new block is sent in full, also to clients that only get borders
        block.setEncodedBlockBorders(BlockState.NEW.equals(block.getBlockState())
                ? lazyFull
                : new LazyEncodedBlock(block, borders, EncodedBlockType.BORDER));
    }

    @Override
    public String getName() {
        return "Caching Encoded Blocks";
    }

    // AlgorithmGolWithSectors keeps the inner cells bit-packed, those rows can be used when they belong to these cells
    private static boolean hasGolRows(Block block) {
        return block.getPackedCells() != null && block.getPackedFrom() == block.getCells();
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

    private static EncodedBlock encode(int x, int y, int generation, byte[] packed, EncodedBlockType type) {
        byte[] compressed = compressor.compress(packed);
        return new EncodedBlock(x, y, generation, Base64.getEncoder().encodeToString(compressed), type.name());
    }

    /**
     * Packs and compresses on the first get (client threads can ask concurrently), then keeps the result.
     */
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
                encoded = encode(x, y, generation, packed.get(), type);
                packed = null;
            }
            return encoded;
        }
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

    /**
     * Same output as getPackedBorders(cells), from bit rows (null = empty row).
     */
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

    private static byte[] getPacked(boolean[][] cells) {
        final int size = cells.length - 2;
        final int wholeWordCells = size & ~63;
        BitStream out = new BitStream(size);
        byte[] row = new byte[size + 64];

        for (int x = 1; x <= size; x++) {
            boolean[] cellRow = cells[x];
            for (int y = 0; y < size; y++) {
                row[y] = (byte) (cellRow[y + 1] ? 1 : 0);
            }
            for (int y = 0; y < wholeWordCells; y += 64) {
                out.write64(pack64(row, y));
            }
            out.writeTail(pack64(row, wholeWordCells));
        }
        return out.finish();
    }

    /**
     * Same output as getPacked(cells), but from AlgorithmGolWithSectors' bit rows: word i + 1 holds columns
     * 64i .. 64i + 63, so the inner cells (columns 1 .. size) are the row shifted down by one bit.
     * A null row is empty.
     */
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

    /**
     * Rows are not word aligned in the output, so bits are streamed out 64 at a time via pending.
     */
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
        Set<Runnable> tasks = blocks.values().stream().map(block -> (Runnable) () -> packAll(block)).collect(Collectors.toSet());

        executorService.executeTasksParallel(tasks, getName());
    }
}
