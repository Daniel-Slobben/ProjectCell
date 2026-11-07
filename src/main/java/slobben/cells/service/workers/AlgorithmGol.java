package slobben.cells.service.workers;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import slobben.cells.entities.Block;
import slobben.cells.enums.BlockState;
import slobben.cells.service.ExecutorService;
import slobben.cells.util.PackedRows;

import java.util.*;

/**
 * Bit-parallel Game of Life on the packed rows of a block. Every long holds 64 cells, so one pass of boolean
 * logic computes 64 cells at once.
 * <p>
 * Fast path (even sector sizes below 64, at most 62 words per row):
 * <ul>
 *     <li>Rows are stepped in pairs. A pair only touches the word range next to the non-zero words of the four rows
 *     involved; the occupancy of a row is a bitmask with one bit per word.</li>
 *     <li>The horizontal neighbour sums of a row (an odd bit and a twos bit per cell) are computed once and kept in
 *     an interleaved ring, although three output rows need them.</li>
 *     <li>New cells are written in place: the old values of the rows above are already folded into the ring.</li>
 *     <li>The new cells of a sector band are added into bit-sliced vertical counters (6 planes, the carry always
 *     runs through all of them); at the end of the band the sector counts are recounted with popcounts per sector
 *     segment.</li>
 *     <li>The row occupancy is cached per block for the next generation; only the border words, which Stitching
 *     changes, are rechecked.</li>
 *     <li>Blocks with a large share of all live cells are split into horizontal strips along sector bands that run in
 *     parallel; each strip uses copies of its boundary rows.</li>
 * </ul>
 * Other configurations use a simple row by row step followed by an absolute recount of the sector counts.
 */
@Service
@RequiredArgsConstructor
public class AlgorithmGol extends Worker {
    private static final int MAX_STRIPS = 4;
    private static final long MIN_STRIP_LIVE_CELLS = 12000L;
    private static final int COUNT_PLANES = 6;

    private final ExecutorService executorService;
    private final Map<String, Block> blocks;

    @Value("${cells.size.blockSize}")
    private int blockSize = 500;
    @Value("${cells.size.sectorSize}")
    private int sectorSize = 100;
    private int sectors;
    private int words;
    private long[] innerMask;
    // per word i the segments segmentStart[i] .. segmentStart[i + 1]: the bits of one sector within that word
    private int[] segmentStart;
    private long[] segmentMask;
    private int[] segmentSector;
    private boolean fastPath;

    private Map<Block, OccupancyCache> occupancyCaches = new IdentityHashMap<>();

    // the occupied words of every row after the last step, trusted when the block was not replaced or changed since
    private static final class OccupancyCache {
        final long[] rowWords;
        long[][] packed;
        int generation;
        boolean valid;

        OccupancyCache(int size) {
            rowWords = new long[size + 2];
        }
    }

    @PostConstruct
    void init() {
        if (blockSize % sectorSize != 0) {
            throw new IllegalStateException();
        }
        sectors = blockSize / sectorSize;
        words = PackedRows.words(blockSize);
        innerMask = PackedRows.innerMask(blockSize);
        segmentStart = new int[words + 2];
        List<Long> masks = new ArrayList<>();
        List<Integer> sectorOfMask = new ArrayList<>();
        for (int i = 1; i <= words; i++) {
            segmentStart[i] = masks.size();
            for (long remaining = innerMask[i]; remaining != 0; remaining &= remaining - 1) {
                int bit = Long.numberOfTrailingZeros(remaining);
                int sector = (((i - 1) << 6) + bit - 1) / sectorSize;
                if (masks.size() == segmentStart[i] || sectorOfMask.getLast() != sector) {
                    masks.add(0L);
                    sectorOfMask.add(sector);
                }
                masks.set(masks.size() - 1, masks.getLast() | 1L << bit);
            }
        }
        segmentStart[words + 1] = masks.size();
        segmentMask = masks.stream().mapToLong(Long::longValue).toArray();
        segmentSector = sectorOfMask.stream().mapToInt(Integer::intValue).toArray();
        fastPath = sectorSize % 2 == 0 && sectorSize < 64 && words >= 2 && words <= 62;
    }

    @Override
    public String getName() {
        return "GOL Algorithm";
    }

    @Override
    public void execute() {
        List<Block> active = new ArrayList<>();
        int[] liveCells = new int[blocks.size()];
        long totalLive = 0;
        for (Block block : blocks.values()) {
            if (block.getBlockState() == BlockState.HIBERNATION) {
                block.setNextHibernationState();
                continue;
            }
            if (block.getSectorCounts() == null) {
                setInitialSectorCounts(block);
            }
            int live = liveCount(block.getSectorCounts());
            liveCells[active.size()] = live;
            active.add(block);
            totalLive += live;
        }
        // aim for about 8 strips over all blocks, so the big blocks keep all threads busy
        long stripTarget = Math.max(MIN_STRIP_LIVE_CELLS, totalLive / 8);
        Map<Block, OccupancyCache> nextCaches = new IdentityHashMap<>();
        Set<Runnable> tasks = new HashSet<>();
        List<Block> splitBlocks = new ArrayList<>();
        for (int n = 0; n < active.size(); n++) {
            final Block block = active.get(n);
            final short[][] sectorCounts = block.getSectorCounts();
            final long[][] packed = block.getPackedCells();
            final OccupancyCache cache = fastPath ? occupancyCache(block, packed) : null;
            if (cache != null) {
                nextCaches.put(block, cache);
            }
            int strips = (int) Math.min(Math.min(MAX_STRIPS, (liveCells[n] + stripTarget - 1) / stripTarget), sectors);
            if (strips <= 1) {
                tasks.add(() -> {
                    processRows(packed, sectorCounts, 1, blockSize, packed[0], packed[blockSize + 1], cache);
                    block.blockUpdated();
                });
                continue;
            }
            for (int j = 0; j < strips; j++) {
                final int first = 1 + (sectors * j / strips) * sectorSize;
                final int last = (sectors * (j + 1) / strips) * sectorSize;
                final long[] above = j == 0 ? packed[0] : packed[first - 1].clone();
                final long[] below = j == strips - 1 ? packed[blockSize + 1] : packed[last + 1].clone();
                tasks.add(() -> processRows(packed, sectorCounts, first, last, above, below, cache));
            }
            splitBlocks.add(block);
        }
        occupancyCaches = nextCaches;
        if (!tasks.isEmpty()) {
            executorService.executeTasksParallel(tasks, getName());
            splitBlocks.forEach(Block::blockUpdated);
        }
    }

    public void setNextState(Block block) {
        if (block.getBlockState() == BlockState.HIBERNATION) {
            block.setNextHibernationState();
            return;
        }
        if (block.getSectorCounts() == null) {
            setInitialSectorCounts(block);
        }
        long[][] packed = block.getPackedCells();
        processRows(packed, block.getSectorCounts(), 1, blockSize, packed[0], packed[blockSize + 1], null);
        block.blockUpdated();
    }

    // the cached occupancy is only trusted when the block was computed by this worker in the previous generation
    private OccupancyCache occupancyCache(Block block, long[][] packed) {
        OccupancyCache cache = occupancyCaches.computeIfAbsent(block, b -> new OccupancyCache(blockSize));
        cache.valid = cache.packed == packed && cache.generation == block.getGeneration()
                && block.getBlockState() == BlockState.ACTIVE;
        cache.packed = packed;
        cache.generation = block.getGeneration() + 1;
        return cache;
    }

    private static int liveCount(short[][] sectorCounts) {
        int live = 0;
        for (short[] row : sectorCounts) {
            for (short count : row) live += count;
        }
        return live;
    }

    private void processRows(long[][] packed, short[][] sectorCounts, int first, int last, long[] above, long[] below,
                             OccupancyCache cache) {
        if (fastPath) {
            stepFast(packed, sectorCounts, first, last, above, below, cache);
        } else {
            stepSimple(packed, sectorCounts, first, last, above, below);
        }
    }

    /**
     * Next state of 64 cells from the horizontal sums (odd and twos bit) of the row above, the own row (which
     * includes the cells themselves) and the row below: alive with 3 neighbours, or alive with 2 neighbours.
     */
    private static long nextState(long aboveOdd, long aboveTwos, long midOdd, long midTwos,
                                  long belowOdd, long belowTwos, long cells) {
        long sideOdd = midOdd ^ cells;
        long sideTwos = midTwos & ~(cells & ~midOdd);
        long half = aboveOdd ^ sideOdd;
        long odd = half ^ belowOdd;
        long carry = (aboveOdd & sideOdd) | (half & belowOdd);
        long twos = aboveTwos ^ sideTwos;
        long fours = aboveTwos & sideTwos;
        long twosBelow = belowTwos ^ carry;
        long foursBelow = belowTwos & carry;
        return ((twos ^ twosBelow) & ~(fours | foursBelow)) & (odd | cells);
    }

    // writes the sum of each cell and its left and right neighbour as odd bit (out[k]) and twos bit (out[k + 1])
    private static void horizontalSum(long[] out, int k, long previous, long cells, long next) {
        long left = (cells << 1) | (previous >>> 63);
        long right = (cells >>> 1) | (next << 63);
        long half = left ^ cells;
        out[k] = half ^ right;
        out[k + 1] = (left & cells) | (half & right);
    }

    private void horizontalSums(long[] row, long[] out, int offset, int stride) {
        for (int i = 1; i <= words; i++) {
            horizontalSum(out, i * stride + offset, row[i - 1], row[i], row[i + 1]);
        }
    }

    private static long nonZero(long word) {
        return (word | -word) >>> 63;
    }

    private long occupiedWords(long[] row) {
        long occupied = 0L;
        for (int i = 1; i <= words; i++) {
            occupied |= nonZero(row[i]) << i;
        }
        return occupied;
    }

    // Stitching only changes the border columns, which live in the first and the last word
    private long occupancy(long[] row, long[] rowOccupancy, int x, boolean trusted) {
        if (!trusted) {
            return occupiedWords(row);
        }
        return (rowOccupancy[x] & ~((1L << 1) | (1L << words))) | nonZero(row[1]) << 1 | nonZero(row[words]) << words;
    }

    private void stepFast(long[][] packed, short[][] sectorCounts, int first, int last, long[] above, long[] below,
                          OccupancyCache cache) {
        long[] sumRing = new long[(words + 2) << 2];
        long[] planes = new long[(words + 2) << 3];
        long[] rowOccupancy = cache == null ? new long[blockSize + 2] : cache.rowWords;
        boolean trusted = cache != null && cache.valid;

        long occAbove = occupiedWords(above);
        long occUpper = occupancy(packed[first], rowOccupancy, first, trusted);
        horizontalSums(above, sumRing, 0, 4);
        horizontalSums(packed[first], sumRing, 2, 4);
        int bandFrom = Integer.MAX_VALUE;
        int bandTo = -1;
        for (int x = first; x < last; x += 2) {
            boolean inside = x + 2 <= last;
            long[] next = inside ? packed[x + 2] : below;
            long occLower = occupancy(packed[x + 1], rowOccupancy, x + 1, trusted);
            long occNext = inside ? occupancy(next, rowOccupancy, x + 2, trusted) : occupiedWords(below);
            long occupied = occAbove | occUpper | occLower | occNext;
            occAbove = occLower;
            occUpper = occNext;
            if (occupied != 0L) {
                int from = Math.max(1, Long.numberOfTrailingZeros(occupied) - 1);
                int to = Math.min(words, 64 - Long.numberOfLeadingZeros(occupied));
                stepRowPair(packed[x], packed[x + 1], next, sumRing, planes, from, to, rowOccupancy, x);
                bandFrom = Math.min(bandFrom, from);
                bandTo = Math.max(bandTo, to);
            } else {
                rowOccupancy[x] = rowOccupancy[x + 1] = 0L;
            }
            if ((x + 1) % sectorSize == 0) {
                recountBand(planes, sectorCounts[x / sectorSize], bandFrom, bandTo);
                bandFrom = Integer.MAX_VALUE;
                bandTo = -1;
            }
        }
    }

    // steps rows x (upper) and x + 1 (lower); the ring holds the sums of rows x - 1 and x and gets those of x + 1, x + 2
    private void stepRowPair(long[] upper, long[] lower, long[] next, long[] sumRing, long[] planes,
                             int from, int to, long[] rowOccupancy, int x) {
        long lowerPrevious = lower[from - 1];
        long upperOccupied = 0L, lowerOccupied = 0L;
        for (int i = from; i <= to; i++) {
            int k = i << 2;
            long aboveOdd = sumRing[k], aboveTwos = sumRing[k + 1];
            long upperOdd = sumRing[k + 2], upperTwos = sumRing[k + 3];
            long lowerCells = lower[i];
            // lower[i - 1] is already overwritten, its old value is carried in lowerPrevious
            horizontalSum(sumRing, k, lowerPrevious, lowerCells, lower[i + 1]);
            horizontalSum(sumRing, k + 2, next[i - 1], next[i], next[i + 1]);
            lowerPrevious = lowerCells;
            long lowerOdd = sumRing[k], lowerTwos = sumRing[k + 1];
            long nextOdd = sumRing[k + 2], nextTwos = sumRing[k + 3];
            if ((aboveOdd | aboveTwos | upperOdd | upperTwos | lowerOdd | lowerTwos | nextOdd | nextTwos) == 0L) {
                continue;
            }
            long upperCells = upper[i];
            long mask = innerMask[i];
            long upperNew = upper[i] = upperCells ^ ((nextState(aboveOdd, aboveTwos, upperOdd, upperTwos,
                    lowerOdd, lowerTwos, upperCells) ^ upperCells) & mask);
            long lowerNew = lower[i] = lowerCells ^ ((nextState(upperOdd, upperTwos, lowerOdd, lowerTwos,
                    nextOdd, nextTwos, lowerCells) ^ lowerCells) & mask);
            upperOccupied |= nonZero(upperNew) << i;
            lowerOccupied |= nonZero(lowerNew) << i;
            if ((upperNew | lowerNew) != 0L) {
                addToPlanes(planes, i << 3, upperNew, lowerNew);
            }
        }
        rowOccupancy[x] = upperOccupied;
        rowOccupancy[x + 1] = lowerOccupied;
    }

    // adds two rows of cells to the bit-sliced per-column counters of one word; the carry runs through all planes,
    // a loop that stops when the carry is zero is mispredicted too often
    private static void addToPlanes(long[] planes, int base, long first, long second) {
        long plane = planes[base];
        long half = plane ^ first;
        planes[base] = half ^ second;
        long carry = (plane & first) | (half & second);
        for (int p = 1; p < COUNT_PLANES; p++) {
            plane = planes[base + p];
            planes[base + p] = plane ^ carry;
            carry &= plane;
        }
    }

    // turns the counters of a finished sector band into its sector counts and clears them for the next band
    private void recountBand(long[] planes, short[] sectorRow, int from, int to) {
        Arrays.fill(sectorRow, (short) 0);
        for (int i = from; i <= to; i++) {
            int base = i << 3;
            long any = 0L;
            for (int p = 0; p < COUNT_PLANES; p++) any |= planes[base + p];
            if (any == 0L) {
                continue;
            }
            for (int s = segmentStart[i], end = segmentStart[i + 1]; s < end; s++) {
                int count = 0;
                for (int p = 0; p < COUNT_PLANES; p++) {
                    count += Long.bitCount(planes[base + p] & segmentMask[s]) << p;
                }
                sectorRow[segmentSector[s]] += (short) count;
            }
            Arrays.fill(planes, base, base + COUNT_PLANES, 0L);
        }
    }

    private void stepSimple(long[][] packed, short[][] sectorCounts, int first, int last, long[] above, long[] below) {
        long[] oldAbove = above.clone();
        long[] aboveSums = new long[(words + 2) << 1];
        long[] rowSums = new long[(words + 2) << 1];
        long[] belowSums = new long[(words + 2) << 1];
        for (int x = first; x <= last; x++) {
            long[] row = packed[x];
            horizontalSums(oldAbove, aboveSums, 0, 2);
            horizontalSums(row, rowSums, 0, 2);
            horizontalSums(x < last ? packed[x + 1] : below, belowSums, 0, 2);
            System.arraycopy(row, 0, oldAbove, 0, row.length);
            for (int i = 1, k = 2; i <= words; i++, k += 2) {
                long cells = row[i];
                long next = nextState(aboveSums[k], aboveSums[k + 1], rowSums[k], rowSums[k + 1],
                        belowSums[k], belowSums[k + 1], cells);
                row[i] = cells ^ ((next ^ cells) & innerMask[i]);
            }
        }
        countSectors(packed, sectorCounts, first, last);
    }

    // absolute recount of the sector bands that rows first .. last cover
    private void countSectors(long[][] packed, short[][] sectorCounts, int first, int last) {
        for (int band = (first - 1) / sectorSize; band <= (last - 1) / sectorSize; band++) {
            Arrays.fill(sectorCounts[band], (short) 0);
        }
        for (int x = first; x <= last; x++) {
            short[] sectorRow = sectorCounts[(x - 1) / sectorSize];
            long[] row = packed[x];
            for (int i = 1; i <= words; i++) {
                if (row[i] == 0L) continue;
                for (int s = segmentStart[i], end = segmentStart[i + 1]; s < end; s++) {
                    sectorRow[segmentSector[s]] += (short) Long.bitCount(row[i] & segmentMask[s]);
                }
            }
        }
    }

    // every step counts all sector bands again, so the counts before the first step are not needed
    private void setInitialSectorCounts(Block block) {
        block.setSectorCounts(new short[sectors][sectors]);
    }
}
