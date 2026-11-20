package slobben.cells.service.workers;

import lombok.RequiredArgsConstructor;
import com.github.luben.zstd.Zstd;
import org.springframework.stereotype.Service;
import slobben.cells.dto.outgoing.EncodedBlock;
import slobben.cells.dto.outgoing.EncodedBlockType;
import slobben.cells.entities.Block;
import slobben.cells.entities.Loop;
import slobben.cells.enums.BlockState;
import slobben.cells.util.PackedRows;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * AI optimized on Blocksize = 2000, sector size = 50.
 * -- below passes the tests
 * Gives every block the encodings a client can ask for, zstd compressed: the full block, as its repeated rows and the
 * runs of its strips (see getFull), its borders, as bit-packed cells, and its sectors, as the alive cells per sector.
 * <p>
 * Encoding is expensive and most blocks are not watched by any client, so a tick only takes a {@link Snapshot} of
 * each block and a client compresses it on first use. The snapshot is the cell array of the block itself: the GoL
 * writes the next generation into the other array of the block and comes back to this one a tick later, after
 * this worker has moved the snapshot on. The same goes for the sector counts the GoL keeps with each array.
 */
@Service
@RequiredArgsConstructor
public class Packer extends Worker {
    private static final VarHandle LONG_LE = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
    private final Map<String, Block> blocks;
    private Map<Block, Snapshot> snapshots = new IdentityHashMap<>();

    public static void packAll(Block block) {
        Snapshot snapshot = new Snapshot();
        snapshot.take(block);
        pack(block, snapshot);
    }

    // a hibernating block has the encodings of its phase
    private static void packHibernating(Block block) {
        Loop.Phase phase = block.getLoop().phase();
        int x = block.getX(), y = block.getY(), generation = block.getGeneration();
        EncodedBlock full = new EncodedBlock(x, y, generation, EncodedBlockType.FULL, phase.full());
        EncodedBlock border = new EncodedBlock(x, y, generation, EncodedBlockType.BORDER, phase.border());
        EncodedBlock sectors = new EncodedBlock(x, y, generation, EncodedBlockType.SECTOR, phase.sectors());
        block.setEncodedBlock(() -> full);
        block.setEncodedBlockBorders(() -> border);
        block.setEncodedBlockSectors(() -> sectors);
    }

    // the cells of a FULL encoding, see getFull
    public static long[][] readFull(byte[] encoded, int size) {
        Cursor in = new Cursor(Zstd.decompress(encoded, (int) Zstd.getFrameContentSize(encoded)));
        long[][] rows = PackedRows.create(size);
        List<int[]> copies = new ArrayList<>();
        for (int x = 0, distance = 0; in.bytes[in.at] != 0; ) {
            x += in.varint();
            int further = in.varint();
            distance += further >>> 1 ^ -(further & 1);
            copies.add(new int[]{x, x - distance});
        }
        int columns = (size + 7) >>> 3;
        in.at++;
        for (int position = 0; in.at < in.bytes.length; in.at++) {
            position += in.varint();
            int strip = position >>> 6, column = 8 * (strip % columns) + 1;
            for (int x = (strip / columns << 6) + (position & 63) + 1; in.bytes[in.at] != 0; in.at++, x++, position++) {
                long[] row = PackedRows.writable(rows, x, rows[0]);
                long cells = in.bytes[in.at] & 0xFFL;
                row[(column >>> 6) + 1] |= cells << column;
                row[(column >>> 6) + 2] |= cells >>> (64 - (column & 63));
            }
        }
        for (int[] copy : copies) rows[copy[0] + 1] = rows[copy[1] + 1].clone();
        return rows;
    }

    private static final class Cursor {
        final byte[] bytes;
        int at;

        Cursor(byte[] bytes) {
            this.bytes = bytes;
        }

        int varint() {
            int value = 0;
            for (int shift = 0; ; shift += 7) {
                byte next = bytes[at++];
                value |= (next & 0x7F) << shift;
                if (next >= 0) return value;
            }
        }
    }

    // the encodings of the cells the snapshot has just taken
    private static void pack(Block block, Snapshot snapshot) {
        LazyEncodedBlock full = new LazyEncodedBlock(block, snapshot, EncodedBlockType.FULL);
        block.setEncodedBlock(full);
        block.setEncodedBlockBorders(block.getBlockState() == BlockState.NEW
                ? full
                : new LazyEncodedBlock(block, snapshot, EncodedBlockType.BORDER));
        // a NEW block has no sector counts until its first GoL step
        block.setEncodedBlockSectors(snapshot.sectorCounts == null
                ? null
                : new LazyEncodedBlock(block, snapshot, EncodedBlockType.SECTOR));
    }

    @Override
    public String getName() {
        return "Packer";
    }

    /**
     * The cells of one block at the tick it was packed. A block keeps its snapshot, the next tick moves it on to
     * the newer cells: an encoding that was not read yet then encodes the newer generation.
     */
    private static final class Snapshot {
        final ReentrantLock lock = new ReentrantLock();
        long[][] rows;
        short[][] sectorCounts;
        int generation;

        void take(Block block) {
            lock.lock();
            try {
                // a block that went into hibernation keeps the cells it had, nothing writes them anymore
                if (block.getPackedCells() == null) return;
                rows = block.getPackedCells();
                sectorCounts = block.getSectorCounts();
                generation = block.getGeneration();
            } finally {
                lock.unlock();
            }
        }

        // false while a client encodes from these cells
        boolean tryTake(Block block) {
            if (!lock.tryLock()) return false;
            try {
                take(block);
            } finally {
                lock.unlock();
            }
            return true;
        }
    }

    private static final class LazyEncodedBlock implements Supplier<EncodedBlock> {
        private final Block block;
        private final Snapshot snapshot;
        private final EncodedBlockType type;
        private EncodedBlock encoded;

        LazyEncodedBlock(Block block, Snapshot snapshot, EncodedBlockType type) {
            this.block = block;
            this.snapshot = snapshot;
            this.type = type;
        }

        @Override
        public synchronized EncodedBlock get() {
            if (encoded == null) {
                byte[] packed;
                int generation;
                snapshot.lock.lock();
                try {
                    // without a tick of this worker in between, the GoL is writing into these cells again
                    if (block.getGeneration() - snapshot.generation >= 2) {
                        snapshot.take(block);
                    }
                    int size = snapshot.rows.length - 2;
                    packed = switch (type) {
                        case FULL -> getFull(snapshot.rows, size);
                        case BORDER -> getPackedBorders(snapshot.rows, size);
                        case SECTOR -> getSectors(snapshot.sectorCounts, size);
                    };
                    generation = snapshot.generation;
                } finally {
                    snapshot.lock.unlock();
                }
                // outside the snapshot, so the next tick of this worker does not wait for the compression
                encoded = new EncodedBlock(block.getX(), block.getY(), generation, type,
                        Zstd.compress(packed));
            }
            return encoded;
        }

        // column size, column 1, then row 1 and row size without the corners
        private static byte[] getPackedBorders(long[][] rows, int size) {
            byte[] packed = new byte[(size * 4 - 4 + 7) / 8];
            int index = 0;
            for (int i = 1; i <= size; i++) index = append(packed, index, PackedRows.get(rows[i], size));
            for (int i = 1; i <= size; i++) index = append(packed, index, PackedRows.get(rows[i], 1));
            for (int i = 2; i < size; i++) index = append(packed, index, PackedRows.get(rows[1], i));
            for (int i = 2; i < size; i++) index = append(packed, index, PackedRows.get(rows[size], i));
            return packed;
        }

        // per band of sectors down the rows, its sectors along the row: the share of alive cells as a u8 of 0-255,
        // rounded up so a sector with any alive cell is above 0
        private static byte[] getSectors(short[][] sectorCounts, int size) {
            int cells = (size / sectorCounts.length) * (size / sectorCounts.length);
            byte[] packed = new byte[sectorCounts.length * sectorCounts.length];
            int i = 0;
            for (short[] band : sectorCounts) {
                for (short count : band) packed[i++] = (byte) ((count * 255 + cells - 1) / cells);
            }
            return packed;
        }

        private static int append(byte[] packed, int index, boolean alive) {
            if (alive) packed[index >>> 3] |= (byte) (1 << (index & 7));
            return index + 1;
        }

        /**
         * The full block before zstd. A row with the same cells as an earlier row is a copy: chaos hits are symmetric or
         * made of lines, so many rows are. Per copy two varints: the rows since the copy before it, and how much further
         * back its earlier row is than that of the copy before it, zigzag. A zero ends the copies.
         * <p>
         * The other rows are read as bytes of 8 cells of a row, bit k for column 8c + k, strip by strip: 8 columns wide
         * and 64 rows tall, each from top to bottom, so byte (x, c) is at (x / 64 * columns + c) * 64 + x % 64. A still
         * life or oscillator is then a few bytes next to each other, the same bytes wherever it is in the block. Per run
         * of live bytes down a strip: a varint of the empty bytes before it, its bytes and a zero.
         */
        private static byte[] getFull(long[][] rows, int size) {
            int columns = (size + 7) >>> 3, words = (size + 63) >>> 6;
            // the cells of the last word that are in the block
            long last = -1L >>> (64 * words - size);
            Bytes copies = new Bytes(64), runs = new Bytes(4096);
            // per slot of a hash: the last row with those cells, + 1
            int[] seen = new int[Integer.highestOneBit(size) * 4];
            long[] hashes = new long[size], cells = new long[words];
            // the strips of one band of 64 rows, and 8 bytes more so every byte can start a long
            byte[] strips = new byte[64 * columns + 8];
            int copied = 0, distance = 0, end = 0;
            for (int top = 0; top < size; top += 64) {
                Arrays.fill(strips, (byte) 0);
                for (int x = top; x < Math.min(top + 64, size); x++) {
                    long[] row = rows[x + 1];
                    for (int i = 0; i < words; i++) cells[i] = row[i + 1] >>> 1 | row[i + 2] << 63;
                    cells[words - 1] &= last;
                    // independent multiplies, so they run side by side
                    long hash = 0, alive = 0;
                    for (int i = 0; i < words; i++) {
                        alive |= cells[i];
                        hash += cells[i] * (0x9E3779B97F4A7C15L + 2L * i);
                    }
                    if (alive == 0) continue;
                    hashes[x] = hash;
                    int slot = (int) (hash >>> 40) & (seen.length - 1);
                    while (seen[slot] != 0 && !sameCells(rows, seen[slot] - 1, x, hashes, words, last)) {
                        slot = (slot + 1) & (seen.length - 1);
                    }
                    if (seen[slot] != 0) {
                        // the nearest earlier row with these cells
                        int earlier = seen[slot] - 1, further = x - earlier - distance;
                        seen[slot] = x + 1;
                        copies.varint(x - copied);
                        copies.varint(further << 1 ^ further >> 31);
                        copied = x;
                        distance = x - earlier;
                        continue;
                    }
                    seen[slot] = x + 1;
                    // all 8 bytes of a word with live cells, without a branch per byte
                    for (int i = 0; i < words - 1; i++) {
                        long word = cells[i];
                        if (word == 0) continue;
                        int base = 8 * 64 * i + x - top;
                        for (int k = 0; k < 8; k++) strips[base + 64 * k] = (byte) (word >>> 8 * k);
                    }
                    // the last word only up to the last column
                    for (long word = cells[words - 1], c = 8L * (words - 1); word != 0; c++, word >>>= 8) {
                        strips[(int) c * 64 + x - top] = (byte) word;
                    }
                }
                int start = top / 64 * columns * 64;
                for (int i = 0; i < 64 * columns; ) {
                    long next = (long) LONG_LE.get(strips, i);
                    if (next == 0) {
                        i += 8;
                        continue;
                    }
                    i += Long.numberOfTrailingZeros(next) >>> 3;
                    if (i >= 64 * columns) break;
                    // a run takes at most 5 + 64 + 1 bytes
                    byte[] out = runs.room(70);
                    int length = runs.length;
                    for (int gap = start + i - end; ; gap >>>= 7) {
                        if (gap >>> 7 == 0) {
                            out[length++] = (byte) gap;
                            break;
                        }
                        out[length++] = (byte) (gap | 0x80);
                    }
                    do out[length++] = strips[i++]; while ((i & 63) != 0 && strips[i] != 0);
                    out[length++] = 0;
                    runs.length = length;
                    end = start + i;
                }
            }
            copies.add(0);
            byte[] full = Arrays.copyOf(copies.bytes, copies.length + runs.length);
            System.arraycopy(runs.bytes, 0, full, copies.length, runs.length);
            return full;
        }

        private static boolean sameCells(long[][] rows, int a, int b, long[] hashes, int words, long last) {
            if (hashes[a] != hashes[b]) return false;
            long[] rowA = rows[a + 1], rowB = rows[b + 1];
            for (int i = 0; i < words; i++) {
                long cellsA = rowA[i + 1] >>> 1 | rowA[i + 2] << 63, cellsB = rowB[i + 1] >>> 1 | rowB[i + 2] << 63;
                if (((cellsA ^ cellsB) & (i < words - 1 ? -1L : last)) != 0) return false;
            }
            return true;
        }

        private static final class Bytes {
            byte[] bytes;
            int length;

            Bytes(int capacity) {
                bytes = new byte[capacity];
            }

            // the bytes, with room for at least that many more
            byte[] room(int more) {
                if (length + more > bytes.length) bytes = Arrays.copyOf(bytes, Math.max(2 * bytes.length, length + more));
                return bytes;
            }

            void add(int value) {
                if (length == bytes.length) bytes = Arrays.copyOf(bytes, 2 * length);
                bytes[length++] = (byte) value;
            }

            void varint(int value) {
                for (; value >>> 7 != 0; value >>>= 7) add(value | 0x80);
                add(value);
            }
        }
    }

    // blocks that are gone are dropped, together with their snapshot
    @Override
    public void execute() {
        Map<Block, Snapshot> current = new IdentityHashMap<>();
        List<Block> busy = new ArrayList<>();
        for (Block block : blocks.values()) {
            if (block.getLoop() != null) {
                packHibernating(block);
                continue;
            }
            Snapshot snapshot = Objects.requireNonNullElseGet(snapshots.get(block), Snapshot::new);
            current.put(block, snapshot);
            if (snapshot.tryTake(block)) {
                pack(block, snapshot);
            } else {
                busy.add(block);
            }
        }
        // a client was encoding these, which is usually done by now
        for (Block block : busy) {
            Snapshot snapshot = current.get(block);
            snapshot.take(block);
            pack(block, snapshot);
        }
        snapshots = current;
    }
}
