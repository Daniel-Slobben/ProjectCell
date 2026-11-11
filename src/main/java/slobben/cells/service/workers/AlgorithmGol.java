package slobben.cells.service.workers;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import slobben.cells.entities.Block;
import slobben.cells.entities.Columns;
import slobben.cells.enums.BlockState;
import slobben.cells.service.ExecutorService;
import slobben.cells.util.PackedRows;

import java.util.*;

/**
 * Bit-parallel Game of Life on the packed rows of a block: a long holds 64 cells, one pass of boolean logic computes
 * them all.
 * <p>
 * A block has two cell arrays. A step reads the cells of the block and writes the next generation over the generation
 * before. So the cells of a block are not written while they are read: the Packer hands out the array itself and
 * only Stitching writes there, the border cells. And where nothing changed in the last two generations, the array that
 * is written already holds the outcome: only the words next to a change are computed.
 * <p>
 * Fast path (even sector sizes below 64, at most 62 words per row): rows are stepped in pairs, sector bands without
 * changes are skipped, the sectors are counted again for the words that changed, and blocks with a large share of the
 * live cells are split into strips that run in parallel. Other configurations step every row and count every sector.
 */
@Service
@RequiredArgsConstructor
public class AlgorithmGol extends Worker {
    private static final int MAX_STRIPS = 4;
    private static final long MIN_STRIP_LIVE_CELLS = 12000L;
    // a block that was not stepped last tick is computed in full twice, until both arrays hold a stepped generation
    private static final int FULL_STEPS = 2;

    private final ExecutorService executorService;
    private final Map<String, Block> blocks;

    @Value("${cells.size.blockSize}")
    private int blockSize = 500;
    @Value("${cells.size.sectorSize}")
    private int sectorSize = 100;
    private int sectors;
    private int words;
    // one bit per word of a row
    private long wordBits;
    // the word of column size
    private int edgeWord;
    private long[] innerMask;
    // per word i the segments segmentStart[i] .. segmentStart[i + 1]: the bits of one sector within that word
    private int[] segmentStart;
    private long[] segmentMask;
    private int[] segmentSector;
    private boolean fastPath;

    // of the blocks that were stepped last tick
    private Map<Block, Frames> known = new IdentityHashMap<>();

    // the cells of a block and the generation before, which the next step overwrites
    private static final class Frames {
        Frame current;
        Frame previous;
        int generation;
        int fullSteps = FULL_STEPS;
    }

    // a cell array and what is known about it
    private final class Frame {
        final long[][] cells;
        // per row: bit i when word i differs from two steps back; bit 0 and bit 63 when that goes for the lowest or
        // the highest cell of a word, a neighbour of the word next to it
        final long[] changed = new long[blockSize + 2];
        final boolean[] changedBands = new boolean[sectors];
        final short[][] sectorCounts = new short[sectors][sectors];
        final short[][] segmentCounts = new short[sectors][segmentMask.length];
        final int[] bandCells = new int[sectors];
        // the border cells at the last step that read these cells, the columns with bit x for row x
        final long[] top = new long[words + 2];
        final long[] bottom = new long[words + 2];
        final long[] left = new long[(blockSize + 64) >>> 6];
        final long[] right = new long[(blockSize + 64) >>> 6];
        // column 1 and column size, for Stitching
        long[] leftEdge = new long[(blockSize + 64) >>> 6];
        long[] rightEdge = new long[(blockSize + 64) >>> 6];

        Frame(long[][] cells) {
            this.cells = cells;
        }

        int liveCells() {
            int live = 0;
            for (int cells : bandCells) live += cells;
            return live;
        }
    }

    @PostConstruct
    void init() {
        if (blockSize % sectorSize != 0) {
            throw new IllegalStateException();
        }
        sectors = blockSize / sectorSize;
        words = PackedRows.words(blockSize);
        edgeWord = (blockSize >>> 6) + 1;
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
        fastPath = sectorSize % 2 == 0 && sectorSize < 64 && words <= 62;
        wordBits = fastPath ? ((1L << words) - 1) << 1 : 0L;
    }

    @Override
    public String getName() {
        return "GOL Algorithm";
    }

    @Override
    public void execute() {
        Map<Block, Frames> stepped = new IdentityHashMap<>();
        long totalLive = 0;
        for (Block block : blocks.values()) {
            if (block.getBlockState() == BlockState.HIBERNATION) {
                block.setNextHibernationState();
                continue;
            }
            Frames frames = frames(block);
            stepped.put(block, frames);
            totalLive += frames.current.liveCells();
        }
        known = stepped;
        // aim for about 8 strips over all blocks, so the big blocks keep all threads busy
        long stripTarget = Math.max(MIN_STRIP_LIVE_CELLS, totalLive / 8);
        Set<Runnable> tasks = new HashSet<>();
        List<Runnable> finishes = new ArrayList<>();
        stepped.forEach((block, frames) -> {
            long strips = Math.min(Math.min(MAX_STRIPS, sectors),
                    (frames.current.liveCells() + stripTarget - 1) / stripTarget);
            if (!fastPath || strips <= 1) {
                tasks.add(() -> step(block, frames));
                return;
            }
            prepare(block, frames);
            for (int j = 0; j < strips; j++) {
                int first = 1 + (int) (sectors * j / strips) * sectorSize;
                int last = (int) (sectors * (j + 1) / strips) * sectorSize;
                tasks.add(() -> stepFast(frames.current, frames.previous, first, last, frames.fullSteps > 0));
            }
            finishes.add(() -> finish(block, frames));
        });
        executorService.executeTasksParallel(tasks, getName());
        finishes.forEach(Runnable::run);
    }

    public void setNextState(Block block) {
        if (block.getBlockState() == BlockState.HIBERNATION) {
            block.setNextHibernationState();
            return;
        }
        Frames frames = frames(block);
        known.put(block, frames);
        step(block, frames);
    }

    // the arrays are only trusted when this worker stepped the block in the previous generation
    private Frames frames(Block block) {
        Frames frames = known.get(block);
        if (frames != null && frames.current.cells == block.getPackedCells()
                && frames.generation == block.getGeneration() && block.getBlockState() == BlockState.ACTIVE) {
            return frames;
        }
        frames = new Frames();
        frames.current = new Frame(block.getPackedCells());
        frames.previous = new Frame(PackedRows.create(blockSize));
        return frames;
    }

    private void step(Block block, Frames frames) {
        prepare(block, frames);
        if (fastPath) {
            stepFast(frames.current, frames.previous, 1, blockSize, frames.fullSteps > 0);
        } else {
            stepSimple(frames.current, frames.previous);
        }
        finish(block, frames);
    }

    private void prepare(Block block, Frames frames) {
        if (!fastPath) return;
        Columns borders = block.getBorders();
        markBorderChanges(frames.current, borders != null && borders.of(block) ? borders : null);
        if (frames.fullSteps > 0) markCells(frames.current, frames.previous);
    }

    private void finish(Block block, Frames frames) {
        Frame next = frames.previous;
        frames.previous = frames.current;
        frames.current = next;
        if (frames.fullSteps > 0) frames.fullSteps--;
        if (fastPath) {
            updateEdges(next);
        } else {
            readEdges(next);
        }
        block.setPackedCells(next.cells);
        block.setSectorCounts(next.sectorCounts);
        block.blockUpdated();
        block.setEdges(new Columns(next.cells, block.getGeneration(), next.leftEdge, next.rightEdge));
        frames.generation = block.getGeneration();
    }

    // Stitching wrote the border cells after the step into these cells: marks where they differ from the border cells
    // at the last step that read the cells. The border columns come from Stitching, or else from the rows.
    private void markBorderChanges(Frame frame, Columns borders) {
        frame.changed[0] = rowChanges(frame.cells[0], frame.top);
        frame.changed[blockSize + 1] = rowChanges(frame.cells[blockSize + 1], frame.bottom);
        frame.changedBands[0] |= frame.changed[0] != 0L;
        frame.changedBands[sectors - 1] |= frame.changed[blockSize + 1] != 0L;
        markColumnChanges(frame, borders != null ? borders.left() : PackedRows.column(frame.cells, 0), frame.left, 1);
        markColumnChanges(frame, borders != null ? borders.right() : PackedRows.column(frame.cells, blockSize + 1),
                frame.right, edgeWord);
    }

    private long rowChanges(long[] row, long[] seen) {
        long marks = 0L;
        for (int i = 1; i <= words; i++) {
            long diff = row[i] ^ seen[i];
            marks |= nonZero(diff) << i | edgeFlags(diff);
            seen[i] = row[i];
        }
        return marks;
    }

    private void markColumnChanges(Frame frame, long[] column, long[] seen, int word) {
        for (int w = 0; w < column.length; w++) {
            for (long rows = column[w] ^ seen[w]; rows != 0L; rows &= rows - 1) {
                int x = (w << 6) + Long.numberOfTrailingZeros(rows);
                frame.changed[x] |= 1L << word;
                frame.changedBands[(x - 1) / sectorSize] = true;
            }
            seen[w] = column[w];
        }
    }

    // nothing is known about the changes: marks every word with cells, in the cells that are read and in those that
    // are overwritten
    private void markCells(Frame source, Frame target) {
        for (int x = 0; x <= blockSize + 1; x++) {
            long[] row = source.cells[x], old = target.cells[x];
            long marks = 0L;
            for (int i = 1; i <= words; i++) {
                marks |= nonZero(row[i] | old[i]) << i | edgeFlags(row[i]);
            }
            source.changed[x] = marks;
        }
        Arrays.fill(source.changedBands, true);
        readEdges(source);
    }

    // the lowest and the highest cell of a word, the neighbours of the words next to it
    private static long edgeFlags(long cells) {
        return cells & (1L | Long.MIN_VALUE);
    }

    // the words to compute for the marks of the rows around them: with an edge flag also the words next to a mark
    private long wordsNear(long marks) {
        long marked = marks & wordBits;
        return (marked | (marked << 1 & -(marks >>> 63)) | (marked >>> 1 & -(marks & 1L))) & wordBits;
    }

    private void stepFast(Frame source, Frame target, int first, int last, boolean full) {
        long[][] cells = source.cells, next = target.cells;
        long[] changed = source.changed, nextChanged = target.changed;
        for (int x = first; x < last; ) {
            int band = (x - 1) / sectorSize;
            // the first and the last row pair of a band look at a row of the band next to it
            if (!source.changedBands[band] && !(band > 0 && source.changedBands[band - 1])
                    && !(band + 1 < sectors && source.changedBands[band + 1])) {
                if (target.changedBands[band]) {
                    Arrays.fill(nextChanged, x, x + sectorSize, 0L);
                    target.changedBands[band] = false;
                }
                x += sectorSize;
                continue;
            }
            long changedWords = full ? wordBits : 0L;
            for (int end = x + sectorSize; x < end; x += 2) {
                long marks = changed[x - 1] | changed[x] | changed[x + 1] | changed[x + 2];
                if (marks == 0L) {
                    nextChanged[x] = nextChanged[x + 1] = 0L;
                } else {
                    changedWords |= stepRowPair(cells[x - 1], cells[x], cells[x + 1], cells[x + 2], next[x],
                            next[x + 1], wordsNear(marks), nextChanged, x);
                }
            }
            target.changedBands[band] = changedWords != 0L;
            if ((changedWords & wordBits) != 0L) recount(target, band, changedWords & wordBits);
        }
    }

    // steps the given words of rows x (upper) and x + 1 (lower), marks and returns the words that differ from the
    // generation that was there
    private long stepRowPair(long[] above, long[] upper, long[] lower, long[] below, long[] nextUpper, long[] nextLower,
                             long todo, long[] nextChanged, int x) {
        long upperChanged = 0L, lowerChanged = 0L;
        while (todo != 0L) {
            // a run of words: each word is read once, and kept as the left neighbour of the next word
            int from = Long.numberOfTrailingZeros(todo);
            int to = from + Long.numberOfTrailingZeros(~(todo >>> from));
            todo &= -1L << to;
            long above0 = above[from - 1], above1 = above[from];
            long upper0 = upper[from - 1], upper1 = upper[from];
            long lower0 = lower[from - 1], lower1 = lower[from];
            long below0 = below[from - 1], below1 = below[from];
            for (int i = from; i < to; i++) {
                long above2 = above[i + 1], upper2 = upper[i + 1], lower2 = lower[i + 1], below2 = below[i + 1];
                long aboveOdd = sumOdd(above0, above1, above2), aboveTwos = sumTwos(above0, above1, above2);
                long upperOdd = sumOdd(upper0, upper1, upper2), upperTwos = sumTwos(upper0, upper1, upper2);
                long lowerOdd = sumOdd(lower0, lower1, lower2), lowerTwos = sumTwos(lower0, lower1, lower2);
                long belowOdd = sumOdd(below0, below1, below2), belowTwos = sumTwos(below0, below1, below2);
                long mask = innerMask[i];
                long oldUpper = nextUpper[i];
                long upperDiff = (nextState(aboveOdd, aboveTwos, upperOdd, upperTwos, lowerOdd, lowerTwos, upper1)
                        ^ oldUpper) & mask;
                nextUpper[i] = oldUpper ^ upperDiff;
                upperChanged |= nonZero(upperDiff) << i | edgeFlags(upperDiff);
                long oldLower = nextLower[i];
                long lowerDiff = (nextState(upperOdd, upperTwos, lowerOdd, lowerTwos, belowOdd, belowTwos, lower1)
                        ^ oldLower) & mask;
                nextLower[i] = oldLower ^ lowerDiff;
                lowerChanged |= nonZero(lowerDiff) << i | edgeFlags(lowerDiff);
                above0 = above1;
                above1 = above2;
                upper0 = upper1;
                upper1 = upper2;
                lower0 = lower1;
                lower1 = lower2;
                below0 = below1;
                below1 = below2;
            }
        }
        nextChanged[x] = upperChanged;
        nextChanged[x + 1] = lowerChanged;
        return upperChanged | lowerChanged;
    }

    private static long nextState(long[] above, long[] row, long[] below, int i) {
        return nextState(sumOdd(above[i - 1], above[i], above[i + 1]), sumTwos(above[i - 1], above[i], above[i + 1]),
                sumOdd(row[i - 1], row[i], row[i + 1]), sumTwos(row[i - 1], row[i], row[i + 1]),
                sumOdd(below[i - 1], below[i], below[i + 1]), sumTwos(below[i - 1], below[i], below[i + 1]), row[i]);
    }

    /**
     * Next state of 64 cells from the horizontal sums (odd and twos bit) of the row above, the own row (which
     * includes the cells themselves) and the row below: alive with 3 neighbours, or alive with 2 neighbours.
     */
    private static long nextState(long aboveOdd, long aboveTwos, long rowOdd, long rowTwos,
                                  long belowOdd, long belowTwos, long cells) {
        long sideOdd = rowOdd ^ cells;
        long sideTwos = rowTwos & ~(cells & ~rowOdd);
        long half = aboveOdd ^ sideOdd;
        long odd = half ^ belowOdd;
        long carry = (aboveOdd & sideOdd) | (half & belowOdd);
        long twos = aboveTwos ^ sideTwos;
        long fours = aboveTwos & sideTwos;
        long twosBelow = belowTwos ^ carry;
        long foursBelow = belowTwos & carry;
        return ((twos ^ twosBelow) & ~(fours | foursBelow)) & (odd | cells);
    }

    // per cell of a word: the sum of the cell and its left and right neighbour, as odd bit and twos bit; the words
    // before and after it hold the neighbours across its edges
    private static long sumOdd(long before, long cells, long after) {
        return (cells << 1 | before >>> 63) ^ cells ^ (cells >>> 1 | after << 63);
    }

    private static long sumTwos(long before, long cells, long after) {
        long left = cells << 1 | before >>> 63;
        return (left & cells) | ((left ^ cells) & (cells >>> 1 | after << 63));
    }

    private static long nonZero(long word) {
        return (word | -word) >>> 63;
    }

    // counts the cells of the given words of a sector band again and adds up the sectors of the band
    private void recount(Frame frame, int band, long columns) {
        long[][] cells = frame.cells;
        short[] segmentCounts = frame.segmentCounts[band];
        int firstRow = band * sectorSize + 1;
        int endRow = firstRow + sectorSize;
        for (; columns != 0L; columns &= columns - 1) {
            int i = Long.numberOfTrailingZeros(columns);
            // bit-sliced counters over the rows of the band: ones holds bit 0 of the count of every column, twos
            // bit 1 and so on
            long ones = 0L, twos = 0L, fours = 0L, eights = 0L, sixteens = 0L, thirtyTwos = 0L;
            for (int x = firstRow; x < endRow; x += 4) {
                long first = cells[x][i], second = cells[x + 1][i], third = 0L, fourth = 0L;
                if (x + 2 < endRow) {
                    third = cells[x + 2][i];
                    fourth = cells[x + 3][i];
                }
                // two rows at a time into the ones, both carries into the twos, that carry runs on through the rest
                long half = ones ^ first;
                long carry = (ones & first) | (half & second);
                ones = half ^ second;
                half = ones ^ third;
                long secondCarry = (ones & third) | (half & fourth);
                ones = half ^ fourth;
                half = twos ^ carry;
                carry = (twos & carry) | (half & secondCarry);
                twos = half ^ secondCarry;
                long keep = fours & carry;
                fours ^= carry;
                carry = keep;
                keep = eights & carry;
                eights ^= carry;
                carry = keep;
                keep = sixteens & carry;
                sixteens ^= carry;
                thirtyTwos ^= keep;
            }
            for (int s = segmentStart[i], end = segmentStart[i + 1]; s < end; s++) {
                long segment = segmentMask[s];
                long counts = wide(byteCounts(ones & segment)) + (wide(byteCounts(twos & segment)) << 1)
                        + (wide(byteCounts(fours & segment)) << 2) + (wide(byteCounts(eights & segment)) << 3)
                        + (wide(byteCounts(sixteens & segment)) << 4) + (wide(byteCounts(thirtyTwos & segment)) << 5);
                segmentCounts[s] = (short) ((counts * 0x0001000100010001L) >>> 48);
            }
        }
        short[] sectorRow = frame.sectorCounts[band];
        Arrays.fill(sectorRow, (short) 0);
        int bandCells = 0;
        for (int s = 0; s < segmentCounts.length; s++) {
            sectorRow[segmentSector[s]] += segmentCounts[s];
            bandCells += segmentCounts[s];
        }
        frame.bandCells[band] = bandCells;
    }

    // the number of set bits per byte; many Long.bitCount calls in a row are slow on ARM
    private static long byteCounts(long bits) {
        bits -= (bits >>> 1) & 0x5555555555555555L;
        bits = (bits & 0x3333333333333333L) + ((bits >>> 2) & 0x3333333333333333L);
        return (bits + (bits >>> 4)) & 0x0f0f0f0f0f0f0f0fL;
    }

    // byte counts added up per two bytes
    private static long wide(long byteCounts) {
        return (byteCounts + (byteCounts >>> 8)) & 0x00ff00ff00ff00ffL;
    }

    // the edge columns of the rows whose first or last word changed
    private void updateEdges(Frame frame) {
        long edgeWords = 1L << 1 | 1L << edgeWord;
        for (int band = 0; band < sectors; band++) {
            if (!frame.changedBands[band]) continue;
            for (int x = band * sectorSize + 1; x <= (band + 1) * sectorSize; x++) {
                if ((frame.changed[x] & edgeWords) == 0L) continue;
                setBit(frame.leftEdge, x, PackedRows.get(frame.cells[x], 1));
                setBit(frame.rightEdge, x, PackedRows.get(frame.cells[x], blockSize));
            }
        }
    }

    private static void setBit(long[] bits, int x, boolean value) {
        bits[x >>> 6] = bits[x >>> 6] & ~(1L << x) | (value ? 1L << x : 0L);
    }

    private void readEdges(Frame frame) {
        frame.leftEdge = PackedRows.column(frame.cells, 1);
        frame.rightEdge = PackedRows.column(frame.cells, blockSize);
    }

    private void stepSimple(Frame source, Frame target) {
        for (int x = 1; x <= blockSize; x++) {
            long[] above = source.cells[x - 1], row = source.cells[x], below = source.cells[x + 1];
            long[] next = target.cells[x];
            for (int i = 1; i <= words; i++) {
                next[i] ^= (nextState(above, row, below, i) ^ next[i]) & innerMask[i];
            }
        }
        for (int band = 0; band < sectors; band++) {
            short[] sectorRow = target.sectorCounts[band];
            Arrays.fill(sectorRow, (short) 0);
            int bandCells = 0;
            for (int x = band * sectorSize + 1; x <= (band + 1) * sectorSize; x++) {
                long[] row = target.cells[x];
                for (int i = 1; i <= words; i++) {
                    if (row[i] == 0L) continue;
                    for (int s = segmentStart[i]; s < segmentStart[i + 1]; s++) {
                        int count = Long.bitCount(row[i] & segmentMask[s]);
                        sectorRow[segmentSector[s]] += (short) count;
                        bandCells += count;
                    }
                }
            }
            target.bandCells[band] = bandCells;
        }
    }
}
