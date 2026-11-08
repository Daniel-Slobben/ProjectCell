package slobben.cells.service.workers;

import lombok.RequiredArgsConstructor;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import org.springframework.stereotype.Service;
import slobben.cells.dto.outgoing.EncodedBlock;
import slobben.cells.dto.outgoing.EncodedBlockType;
import slobben.cells.entities.Block;
import slobben.cells.enums.BlockState;
import slobben.cells.service.ExecutorService;
import slobben.cells.util.PackedRows;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.*;
import java.util.function.Supplier;

/**
 * Gives every block the encodings a client can ask for: the full block and its borders, as bit-packed, LZ4
 * compressed cells.
 * <p>
 * Encoding is expensive and most blocks are not watched by any client, so a tick only takes a {@link Snapshot} of
 * each block and a client compresses it on first use. The snapshot is a copy, because the GoL changes the packed
 * cells in place next tick. Taking it is the cost of this worker, so the snapshot reuses its buffers every tick and
 * skips the bands of rows that the sector counts show to be empty, without reading them.
 */
@Service
@RequiredArgsConstructor
public class Packer extends Worker {
    private static final LZ4Compressor compressor = LZ4Factory.fastestInstance().fastCompressor();

    private static final VarHandle LONG_LE = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
    private final Map<String, Block> blocks;
    private final ExecutorService executorService;
    private Map<Block, Snapshot> snapshots = new IdentityHashMap<>();

    public static void packAll(Block block) {
        pack(block, new Snapshot(block.getPackedCells().length - 2));
    }

    private static void pack(Block block, Snapshot snapshot) {
        synchronized (snapshot) {
            snapshot.fill(block);
        }
        LazyEncodedBlock full = new LazyEncodedBlock(block, snapshot, EncodedBlockType.FULL);
        block.setEncodedBlock(full);
        // a NEW block is sent in full, also as its borders
        block.setEncodedBlockBorders(block.getBlockState() == BlockState.NEW
                ? full
                : new LazyEncodedBlock(block, snapshot, EncodedBlockType.BORDER));
    }

    @Override
    public String getName() {
        return "Caching Encoded Blocks";
    }

    /**
     * A copy of the rows of one block that is reused every tick, so packing allocates no new arrays after the first
     * tick. Rows that are known to be empty are null. The next tick overwrites the copy: an encoding that was not
     * read yet then encodes the newer generation.
     */
    private static final class Snapshot {
        final int size;
        final long[][] rows;
        final long[][] buffers;
        int generation;

        Snapshot(int size) {
            this.size = size;
            this.rows = new long[size + 2][];
            this.buffers = new long[size + 2][];
        }

        /**
         * With exact sector counts a band of rows whose sectors are all 0 is empty and not read, and the rows of the
         * other bands are copied without looking, which is faster than checking them first. Without exact counts a
         * row is only copied when it has cells.
         */
        void fill(Block block) {
            generation = block.getGeneration();
            long[][] packedCells = block.getPackedCells();
            short[][] counts = exactSectorCounts(block);
            int bandRows = counts == null ? size : size / counts.length;
            for (int band = 0, first = 1; first <= size; band++, first += bandRows) {
                int end = first + bandRows;
                if (counts != null && isEmpty(counts[band])) {
                    Arrays.fill(rows, first, end, null);
                    continue;
                }
                for (int x = first; x < end; x++) {
                    long[] row = packedCells[x];
                    rows[x] = counts == null && isEmpty(row) ? null : copy(x, row);
                }
            }
        }

        // the GoL recounts the sectors of every block it steps and only it changes the inner cells of ACTIVE blocks,
        // the counts of NEW and HIBERNATION blocks may be stale
        private short[][] exactSectorCounts(Block block) {
            short[][] counts = block.getSectorCounts();
            boolean exact = block.getBlockState() == BlockState.ACTIVE && counts != null
                    && counts.length > 0 && size % counts.length == 0;
            return exact ? counts : null;
        }

        private long[] copy(int x, long[] row) {
            long[] buffer = buffers[x];
            if (buffer == null || buffer.length != row.length) {
                buffer = buffers[x] = new long[row.length];
            }
            // the first and last word are always-zero padding
            System.arraycopy(row, 1, buffer, 1, row.length - 2);
            return buffer;
        }

        private static boolean isEmpty(short[] counts) {
            for (short count : counts) {
                if (count != 0) return false;
            }
            return true;
        }

        private static boolean isEmpty(long[] row) {
            for (long word : row) {
                if (word != 0) return false;
            }
            return true;
        }
    }

    private static final class LazyEncodedBlock implements Supplier<EncodedBlock> {
        private final int x;
        private final int y;
        private final Snapshot snapshot;
        private final EncodedBlockType type;
        private EncodedBlock encoded;

        LazyEncodedBlock(Block block, Snapshot snapshot, EncodedBlockType type) {
            this.x = block.getX();
            this.y = block.getY();
            this.snapshot = snapshot;
            this.type = type;
        }

        @Override
        public synchronized EncodedBlock get() {
            if (encoded == null) {
                synchronized (snapshot) {
                    byte[] packed = type == EncodedBlockType.FULL
                            ? getPacked(snapshot.rows, snapshot.size)
                            : getPackedBorders(snapshot.rows, snapshot.size);
                    encoded = new EncodedBlock(x, y, snapshot.generation, type, compressor.compress(packed));
                }
            }
            return encoded;
        }
    }

    // column size, column 1, then row 1 and row size without the corners
    private static byte[] getPackedBorders(long[][] rows, int size) {
        byte[] packed = new byte[(size * 4 - 4 + 7) / 8];
        int index = 0;
        for (int i = 1; i <= size; i++) index = append(packed, index, bit(rows[i], size));
        for (int i = 1; i <= size; i++) index = append(packed, index, bit(rows[i], 1));
        for (int i = 2; i < size; i++) index = append(packed, index, bit(rows[1], i));
        for (int i = 2; i < size; i++) index = append(packed, index, bit(rows[size], i));
        return packed;
    }

    private static int append(byte[] packed, int index, boolean alive) {
        if (alive) packed[index >>> 3] |= (byte) (1 << (index & 7));
        return index + 1;
    }

    private static boolean bit(long[] row, int column) {
        return row != null && PackedRows.get(row, column);
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

    /**
     * Looks up the snapshot of every block on the calling thread, so the tasks never touch the map. Blocks that are
     * gone are dropped from the map, together with their snapshot.
     */
    @Override
    public void execute() {
        Map<Block, Snapshot> current = new IdentityHashMap<>();
        Set<Runnable> tasks = new HashSet<>();
        for (Block block : blocks.values()) {
            int size = block.getPackedCells().length - 2;
            Snapshot snapshot = snapshots.get(block);
            if (snapshot == null || snapshot.size != size) {
                snapshot = new Snapshot(size);
            }
            current.put(block, snapshot);
            Snapshot blockSnapshot = snapshot;
            tasks.add(() -> pack(block, blockSnapshot));
        }
        snapshots = current;

        executorService.executeTasksParallel(tasks, getName());
    }
}
