package slobben.cells.service.workers;

import lombok.RequiredArgsConstructor;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import org.springframework.stereotype.Service;
import slobben.cells.dto.outgoing.EncodedBlock;
import slobben.cells.dto.outgoing.EncodedBlockType;
import slobben.cells.entities.Block;
import slobben.cells.enums.BlockState;
import slobben.cells.util.PackedRows;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * AI optimized on Blocksize = 2000, sector size = 50.
 * -- below passes the tests
 * Gives every block the encodings a client can ask for: the full block and its borders, as bit-packed, LZ4
 * compressed cells.
 * <p>
 * Encoding is expensive and most blocks are not watched by any client, so a tick only takes a {@link Snapshot} of
 * each block and a client compresses it on first use. The snapshot is the cell array of the block itself: the GoL
 * writes the next generation into the other array of the block and comes back to this one a tick later, after
 * this worker has moved the snapshot on.
 */
@Service
@RequiredArgsConstructor
public class Packer extends Worker {
    private static final LZ4Compressor compressor = LZ4Factory.fastestInstance().fastCompressor();

    private static final VarHandle LONG_LE = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
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
                synchronized (snapshot) {
                    // without a tick of this worker in between, the GoL is writing into these cells again
                    if (block.getGeneration() - snapshot.generation >= 2) {
                        snapshot.take(block);
                    }
                    int size = snapshot.rows.length - 2;
                    byte[] packed = type == EncodedBlockType.FULL
                            ? getPacked(snapshot.rows, size)
                            : getPackedBorders(snapshot.rows, size);
                    encoded = new EncodedBlock(block.getX(), block.getY(), snapshot.generation, type,
                            compressor.compress(packed));
                }
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

        private static byte[] getPacked(long[][] packedCells, int size) {
            final int wholeWords = size >>> 6;
            final long tailMask = (1L << (size & 63)) - 1;
            BitStream out = new BitStream(size);

            for (int x = 1; x <= size; x++) {
                long[] row = packedCells[x];
                for (int i = 1; i <= wholeWords; i++) {
                    out.write64(row[i] >>> 1 | row[i + 1] << 63);
                }
                // masks off the right border column
                out.writeTail((row[wholeWords + 1] >>> 1 | row[wholeWords + 2] << 63) & tailMask);
            }
            return out.finish();
        }

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
