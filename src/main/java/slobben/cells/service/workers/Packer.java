package slobben.cells.service.workers;

import lombok.RequiredArgsConstructor;
import com.github.luben.zstd.Zstd;
import org.springframework.stereotype.Service;
import slobben.cells.dto.outgoing.EncodedBlock;
import slobben.cells.dto.outgoing.EncodedBlockType;
import slobben.cells.entities.Block;
import slobben.cells.enums.BlockState;
import slobben.cells.util.PackedRows;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * AI optimized on Blocksize = 2000, sector size = 50.
 * -- below passes the tests
 * Gives every block the encodings a client can ask for, zstd compressed: the full block, as its repeated rows and the
 * runs of its strips (see getStrips), and its borders, as bit-packed cells.
 * <p>
 * Encoding is expensive and most blocks are not watched by any client, so a tick only takes a {@link Snapshot} of
 * each block and a client compresses it on first use. The snapshot is the cell array of the block itself: the GoL
 * writes the next generation into the other array of the block and comes back to this one a tick later, after
 * this worker has moved the snapshot on.
 */
@Service
@RequiredArgsConstructor
public class Packer extends Worker {
    private final Map<String, Block> blocks;
    private Map<Block, Snapshot> snapshots = new IdentityHashMap<>();

    public static void packAll(Block block) {
        pack(block, new Snapshot());
    }

    private static void pack(Block block, Snapshot snapshot) {
        snapshot.take(block);
        LazyEncodedBlock full = new LazyEncodedBlock(block, snapshot, EncodedBlockType.FULL);
        block.setEncodedBlock(full);
        block.setEncodedBlockBorders(block.getBlockState() == BlockState.NEW
                ? full
                : new LazyEncodedBlock(block, snapshot, EncodedBlockType.BORDER));
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
        long[][] rows;
        int generation;

        synchronized void take(Block block) {
            rows = block.getPackedCells();
            generation = block.getGeneration();
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
                ByteArrayOutputStream copies = new ByteArrayOutputStream();
                int generation;
                synchronized (snapshot) {
                    // without a tick of this worker in between, the GoL is writing into these cells again
                    if (block.getGeneration() - snapshot.generation >= 2) {
                        snapshot.take(block);
                    }
                    int size = snapshot.rows.length - 2;
                    packed = type == EncodedBlockType.FULL
                            ? getStrips(snapshot.rows, size, copies)
                            : getPackedBorders(snapshot.rows, size);
                    generation = snapshot.generation;
                }
                // outside the snapshot, so the next tick of this worker does not wait for the compression
                encoded = new EncodedBlock(block.getX(), block.getY(), generation, type,
                        Zstd.compress(type == EncodedBlockType.FULL ? getRuns(packed, copies.toByteArray()) : packed));
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

        private static int append(byte[] packed, int index, boolean alive) {
            if (alive) packed[index >>> 3] |= (byte) (1 << (index & 7));
            return index + 1;
        }

        /**
         * The cells as bytes of 8 cells of a row, bit k for column 8c + k, strip by strip: 8 columns wide and 64 rows
         * tall, each from top to bottom, so byte (x, c) is at (x / 64 * columns + c) * 64 + x % 64. A still life or
         * oscillator is then a few bytes next to each other, the same bytes wherever it is in the block.
         * <p>
         * A row with the same cells as an earlier row stays out of the strips: chaos hits are symmetric or made of
         * lines, so many rows are. Copies gets two varints for it: the rows since the copied row before it, and how
         * much further back its earlier row is than that of the copied row before it, zigzag.
         */
        private static byte[] getStrips(long[][] rows, int size, ByteArrayOutputStream copies) {
            int columns = (size + 7) >>> 3, words = (size + 63) >>> 6;
            // the cells of the last word that are in the block
            long last = -1L >>> (64 * words - size);
            byte[] strips = new byte[(size + 63) / 64 * 64 * columns];
            Map<Row, Integer> seen = new HashMap<>();
            int copied = 0, distance = 0;
            for (int x = 0; x < size; x++) {
                long[] row = rows[x + 1], cells = new long[words];
                long alive = 0;
                for (int i = 0; i < words; i++) {
                    cells[i] = (row[i + 1] >>> 1 | row[i + 2] << 63) & (i < words - 1 ? -1L : last);
                    alive |= cells[i];
                }
                if (alive == 0) continue;
                Integer earlier = seen.put(new Row(cells), x);
                if (earlier != null) {
                    int further = x - earlier - distance;
                    writeVarint(copies, x - copied);
                    writeVarint(copies, further << 1 ^ further >> 31);
                    copied = x;
                    distance = x - earlier;
                    continue;
                }
                int at = x / 64 * columns * 64 + x % 64;
                for (int i = 0; i < words; i++) {
                    long word = cells[i];
                    for (int c = 8 * i; word != 0; c++, word >>>= 8) strips[at + 64 * c] = (byte) word;
                }
            }
            return strips;
        }

        // the cells of a row, equal when the cells are
        private record Row(long[] cells) {
            @Override
            public boolean equals(Object other) {
                return other instanceof Row row && Arrays.equals(cells, row.cells);
            }

            @Override
            public int hashCode() {
                return Arrays.hashCode(cells);
            }
        }

        private static void writeVarint(ByteArrayOutputStream out, int value) {
            for (; value >>> 7 != 0; value >>>= 7) out.write(value | 0x80);
            out.write(value);
        }

        // the copies and a zero, then per run of live bytes down a strip: varint the empty bytes before it, its bytes
        // and a zero
        private static byte[] getRuns(byte[] strips, byte[] copies) {
            byte[] runs = Arrays.copyOf(copies, copies.length + strips.length / 8 + 71);
            int length = copies.length + 1;
            for (int i = 0, end = 0; i < strips.length; ) {
                if (strips[i] == 0) {
                    i++;
                    continue;
                }
                // a run takes at most 5 + 64 + 1 bytes
                if (length + 70 > runs.length) runs = Arrays.copyOf(runs, 2 * runs.length);
                int gap = i - end;
                for (; gap >= 0x80; gap >>>= 7) runs[length++] = (byte) (gap | 0x80);
                runs[length++] = (byte) gap;
                do runs[length++] = strips[i++]; while (i % 64 != 0 && strips[i] != 0);
                runs[length++] = 0;
                end = i;
            }
            return Arrays.copyOf(runs, length);
        }
    }

    // blocks that are gone are dropped, together with their snapshot
    @Override
    public void execute() {
        Map<Block, Snapshot> current = new IdentityHashMap<>();
        for (Block block : blocks.values()) {
            Snapshot snapshot = Objects.requireNonNullElseGet(snapshots.get(block), Snapshot::new);
            current.put(block, snapshot);
            pack(block, snapshot);
        }
        snapshots = current;
    }
}
