package slobben.cells.service.workers;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import slobben.cells.entities.Block;
import slobben.cells.enums.BlockState;
import slobben.cells.service.ExecutorService;
import slobben.cells.util.PackedRows;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Bit-parallel Game of Life. The neighbor count is done for 64 cells at once with bitwise adders.
 * Only cells that actually change update the sector counts.
 */
@Service
@RequiredArgsConstructor
public class AlgorithmGol extends Worker {

    private final ExecutorService executorService;
    private final Map<String, Block> blocks;

    @Value("${cells.size.blockSize}")
    private int blockSize = 500;
    @Value("${cells.size.sectorSize}")
    private int sectorSize = 100;
    private int totalSectors = 0;

    private int words;
    private long[] innerMask;
    private int[] columnSector;

    @PostConstruct
    void init() {
        if (blockSize % sectorSize != 0) {
            throw new IllegalStateException();
        }
        totalSectors = blockSize / sectorSize;
        words = PackedRows.words(blockSize);
        innerMask = PackedRows.innerMask(blockSize);
        columnSector = new int[blockSize + 2];
        for (int y = 1; y <= blockSize; y++) {
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
        if (block.getSectorCounts() == null) {
            setInitialSectorHeat(block);
        }
        short[][] sectorCounts = block.getSectorCounts();
        long[][] packed = block.getPackedCells();

        long[] previousOriginal = packed[0].clone();
        long[] currentOriginal = new long[words + 2];
        for (int x = 1; x <= blockSize; x++) {
            long[] row = packed[x];
            // row x is overwritten below, but row x + 1 still needs the original as its upper neighbour
            System.arraycopy(row, 0, currentOriginal, 0, words + 2);
            nextRow(previousOriginal, currentOriginal, packed[x + 1], row, sectorCounts[(x - 1) / sectorSize]);
            long[] swap = previousOriginal;
            previousOriginal = currentOriginal;
            currentOriginal = swap;
        }

        block.blockUpdated();
    }

    private void setInitialSectorHeat(Block block) {
        short[][] sector = new short[totalSectors][totalSectors];
        long[][] packed = block.getPackedCells();
        for (int x = 1; x <= blockSize; x++) {
            short[] sectorRow = sector[(x - 1) / sectorSize];
            for (int i = 1; i <= words; i++) {
                long alive = packed[x][i] & innerMask[i];
                int base = (i - 1) << 6;
                while (alive != 0) {
                    sectorRow[columnSector[base + Long.numberOfTrailingZeros(alive)]]++;
                    alive &= alive - 1;
                }
            }
        }
        block.setSectorCounts(sector);
    }

    /**
     * Next generation of one row, calculates 64 cells per iteration. (1 long)
     * Writes the new words to {@code out} and counts only the changed cells in {@code sectorRow}.
     */
    private void nextRow(long[] upperRow, long[] middleRow, long[] downRow, long[] out, short[] sectorRow) {
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
            long nextWord = middleWord ^ changed;
            out[i] = nextWord;

            int base = (i - 1) << 6;
            do {
                int bit = Long.numberOfTrailingZeros(changed);
                boolean alive = (nextWord >>> bit & 1) != 0;
                sectorRow[columnSector[base + bit]] += (short) (alive ? 1 : -1);
                changed &= changed - 1;
            } while (changed != 0);
        }
    }
}
