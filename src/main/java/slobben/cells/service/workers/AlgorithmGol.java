package slobben.cells.service.workers;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import slobben.cells.entities.Block;
import slobben.cells.enums.BlockState;
import slobben.cells.service.ExecutorService;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Bit-parallel Game of Life. The neighbor count is done for 64 cells at once with bitwise adders.
 * Only cells that actually change are written back to the boolean[][] and the sector counts.
 */
@Service
@RequiredArgsConstructor
public class AlgorithmGol implements Worker {

    private final ExecutorService executorService;
    private final Map<String, Block> blocks;

    @Value("${cells.size.blockSize}")
    private int blockSize = 500;
    @Value("${cells.size.sectorSize}")
    private int sectorSize = 100;
    private int totalSectors = 0;

    // packed row layout: word i + 1 holds columns 64i .. 64i + 63, words 0 and words + 1 are always-zero padding
    private int width;
    private int words;
    private long[] innerMask;
    private int[] columnSector;

    @PostConstruct
    void init() {
        if (blockSize % sectorSize != 0) {
            throw new IllegalStateException();
        }
        totalSectors = blockSize / sectorSize;
        width = blockSize + 2;
        words = (width + 63) >>> 6;
        innerMask = new long[words + 2];
        columnSector = new int[width];
        for (int y = 1; y <= blockSize; y++) {
            innerMask[(y >>> 6) + 1] |= 1L << y;
            columnSector[y] = (y - 1) / sectorSize;
        }
    }

    @Override
    public String getName() {
        return "GOL Algorithm";
    }

    @Override
    public void execute() {
        Set<Runnable> tasks = blocks.values().stream().map(block -> ((Runnable) () -> setNextState(block))).collect(Collectors.toSet());
        executorService.executeTasksParallel(tasks, getName());
    }

    public void setNextState(Block block) {
        if (block.getBlockState() == BlockState.HIBERNATION) {
            block.setNextHibernationState();
            return;
        }
        boolean[][] cells = block.getCells();
        if (block.getSectorCounts() == null) {
            setInitialSectorHeat(block);
        }
        short[][] sectorCounts = block.getSectorCounts();

        if (block.getPackedCells() == null || block.getPackedFrom() != cells) {
            long[][] packed = new long[width][words + 2];
            packInner(cells, sectorCounts, packed);
            block.setPackedCells(packed);
            block.setPackedFrom(cells);
        }
        long[][] packed = block.getPackedCells();
        packBorders(cells, packed);

        long[] previousOriginal = packed[0].clone();
        long[] currentOriginal = new long[words + 2];
        for (int x = 1; x <= blockSize; x++) {
            long[] row = packed[x];
            // row x is overwritten below, but row x + 1 still needs the original as its upper neighbour
            System.arraycopy(row, 0, currentOriginal, 0, words + 2);
            nextRow(previousOriginal, currentOriginal, packed[x + 1], row, cells[x], sectorCounts[(x - 1) / sectorSize]);
            long[] swap = previousOriginal;
            previousOriginal = currentOriginal;
            currentOriginal = swap;
        }

        block.blockUpdated();
    }

    private void setInitialSectorHeat(Block block) {
        block.setSectorCounts(new short[totalSectors][totalSectors]);
        boolean[][] matrix = block.getCells();
        short[][] sector = block.getSectorCounts();
        for (int x = 1; x <= blockSize; x++) {
            for (int y = 1; y <= blockSize; y++) {
                if (!matrix[x][y]) continue;
                sector[(x - 1) / sectorSize][(y - 1) / sectorSize]++;
            }
        }
    }

    /**
     * Next generation of one row, calculates 64 cells per iteration. (1 long)
     * Writes the new words to {@code out} and flips only the changed cells in {@code cellRow}.
     */
    private void nextRow(long[] upperRow, long[] middleRow, long[] downRow, long[] out, boolean[] cellRow, short[] sectorRow) {
        for (int i = 1; i <= words; i++) {
            // neighbours to the left / right: shift the word and pull in the edge bit of the adjacent word
            long upperWord = upperRow[i];
            long upperWordLeft = (upperWord << 1) | (upperRow[i - 1] >>> 63);
            long upperWordRight = (upperWord >>> 1) | (upperRow[i + 1] << 63);
            long middleWord = middleRow[i];
            long middleWordLeft = (middleWord << 1) | (middleRow[i - 1] >>> 63);
            long middleWordRight = (middleWord >>> 1) | (middleRow[i + 1] << 63);
            long downWord = downRow[i];
            long downWordLeft = (downWord << 1) | (downRow[i - 1] >>> 63);
            long downWordRight = (downWord >>> 1) | (downRow[i + 1] << 63);

            // per row: 2-bit sum of the neighbors in that row (twos bit: 0 or 1 neighbors = 0. 2 or 3 neighbors = 1)
            long upperEitherLeftOrMiddle = upperWordLeft ^ upperWord;
            long upperOddNumberAlive = upperEitherLeftOrMiddle ^ upperWordRight;
            long upperTwosBitNeighbours = (upperWordLeft & upperWord) | (upperEitherLeftOrMiddle & upperWordRight);
            long middleOddNumberAlive = middleWordLeft ^ middleWordRight;
            long middleTwosBit = middleWordLeft & middleWordRight;
            long downEitherLeftOrMiddle = downWordLeft ^ downWord;
            long downOddNumberAlive = downEitherLeftOrMiddle ^ downWordRight;
            long downTwosBitNeighbours = (downWordLeft & downWord) | (downEitherLeftOrMiddle & downWordRight);

            long upperOrMiddleIsOdd = upperOddNumberAlive ^ middleOddNumberAlive;
            long combinedOddNumberAlive = upperOrMiddleIsOdd ^ downOddNumberAlive;
            long combinedOddNumberCarry = (upperOddNumberAlive & middleOddNumberAlive) | (upperOrMiddleIsOdd & downOddNumberAlive);

            // 2 or 3 neighbours <=> exactly one of the four "twos" bits is set
            long p = upperTwosBitNeighbours ^ middleTwosBit;
            long q = upperTwosBitNeighbours & middleTwosBit;
            long r = downTwosBitNeighbours ^ combinedOddNumberCarry;
            long t = downTwosBitNeighbours & combinedOddNumberCarry;
            long twoOrThree = (p ^ r) & ~(q | t);

            // 3 neighbours: born or survives, 2 neighbours: survives only if alive
            long next = twoOrThree & (combinedOddNumberAlive | middleWord);
            long changed = (next ^ middleWord) & innerMask[i];
            if (changed == 0) continue;
            out[i] = middleWord ^ changed;

            int base = (i - 1) << 6;
            do {
                int y = base + Long.numberOfTrailingZeros(changed);
                boolean alive = !cellRow[y];
                cellRow[y] = alive;
                sectorRow[columnSector[y]] += (short) (alive ? 1 : -1);
                changed &= changed - 1;
            } while (changed != 0);
        }
    }

    private void packInner(boolean[][] cells, short[][] sectorCounts, long[][] packed) {
        for (int sx = 0; sx < totalSectors; sx++) {
            for (int sy = 0; sy < totalSectors; sy++) {
                if (sectorCounts[sx][sy] == 0) continue;
                int startX = sx * sectorSize + 1;
                int startY = sy * sectorSize + 1;
                for (int x = startX; x < startX + sectorSize; x++) {
                    packRange(cells[x], packed[x], startY, startY + sectorSize);
                }
            }
        }
    }

    // the borders are rewritten by Stitching every tick, so they are always re-read
    private void packBorders(boolean[][] cells, long[][] packed) {
        int last = width - 1;
        Arrays.fill(packed[0], 0L);
        Arrays.fill(packed[last], 0L);
        packRange(cells[0], packed[0], 0, width);
        packRange(cells[last], packed[last], 0, width);
        int lastWord = (last >>> 6) + 1;
        long lastBit = 1L << last;
        for (int x = 1; x < last; x++) {
            long[] row = packed[x];
            row[1] = (row[1] & ~1L) | (cells[x][0] ? 1L : 0L);
            row[lastWord] = (row[lastWord] & ~lastBit) | (cells[x][last] ? lastBit : 0L);
        }
    }

    /**
     * ORs cells[from..to) into the packed row.
     */
    private static void packRange(boolean[] cells, long[] packed, int from, int to) {
        int y = from;
        while (y < to) {
            int end = Math.min(to, ((y >>> 6) + 1) << 6);
            long word = 0;
            for (int b = y; b < end; b++) {
                word |= (cells[b] ? 1L : 0L) << b;
            }
            packed[(y >>> 6) + 1] |= word;
            y = end;
        }
    }
}
