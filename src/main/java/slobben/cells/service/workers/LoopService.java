package slobben.cells.service.workers;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import slobben.cells.entities.Block;
import slobben.cells.entities.Loop;
import slobben.cells.enums.BlockState;
import slobben.cells.util.PackedRows;

import java.util.*;

/**
 * Finds the blocks whose cells and borders come back after a number of generations, and lets them hibernate: they
 * keep the encodings, edges and borders of each generation of the loop instead of their cells, and the workers play
 * the loop back. A hibernating block wakes up when a neighbour writes other borders than those of its loop.
 * <p>
 * Every checkEvery generations the cells of a block are hashed. Cells that are the same as checkEvery generations
 * before are hashed every generation until they come back, which gives the period. The next period is recorded, and
 * the block hibernates when the generation after it is the first one again.
 */
@Service
@RequiredArgsConstructor
public class LoopService extends Worker {
    private final Map<String, Block> blocks;
    @Value("${cells.size.blockSize}")
    private int blockSize;
    // a multiple of the periods of common oscillators: 1, 2, 3, 4, 5, 6, 8, 15 and 30 among them
    @Value("${cells.loops.check-every:120}")
    private int checkEvery = 120;
    private long[] emptyRow;
    private final Map<Block, Long> hashes = new IdentityHashMap<>();
    private final Map<Block, Recording> recordings = new IdentityHashMap<>();
    private int ticks = 0;
    @Getter
    private int hibernating = 0;

    private static final class Recording {
        final long hash;
        final int start;
        int period;
        final List<Loop.Phase> phases = new ArrayList<>();

        Recording(long hash, int start) {
            this.hash = hash;
            this.start = start;
        }
    }

    @PostConstruct
    void init() {
        emptyRow = PackedRows.emptyRow(blockSize);
    }

    @Override
    public String getName() {
        return "Loops with %d blocks hibernating".formatted(hibernating);
    }

    @Override
    public void execute() {
        int count = 0;
        for (Block block : blocks.values()) {
            if (block.getBlockState() == BlockState.HIBERNATION) {
                Loop loop = block.getLoop();
                if (loop.received().same(loop.phase().borders())) {
                    count++;
                } else {
                    wake(block);
                }
            } else if (block.getBlockState() == BlockState.ACTIVE
                    && (block.getGeneration() + block.getX() + block.getY()) % checkEvery == 0
                    && !recordings.containsKey(block)) {
                check(block);
            }
        }
        recordings.entrySet().removeIf(entry -> !record(entry.getKey(), entry.getValue()));
        hibernating = count;
        if (++ticks % checkEvery == 0) hashes.keySet().removeIf(block -> blocks.get(block.getKey()) != block);
    }

    private void check(Block block) {
        long hash = hash(block.getPackedCells());
        Long before = hashes.put(block, hash);
        if (before != null && before == hash && hasCells(block.getSectorCounts())) {
            recordings.put(block, new Recording(hash, block.getGeneration()));
        }
    }

    // false once the block hibernates or turns out not to loop
    private boolean record(Block block, Recording recording) {
        if (blocks.get(block.getKey()) != block) return false;
        long[][] cells = block.getPackedCells();
        if (recording.period == 0) {
            int generations = block.getGeneration() - recording.start;
            if (generations == 0 || hash(cells) != recording.hash) return generations < checkEvery;
            recording.period = generations;
        }
        if (recording.phases.size() < recording.period) {
            recording.phases.add(new Loop.Phase(block.getEncodedBlock().encodedCells(),
                    block.getEncodedBlockBorders().encodedCells(), block.getEncodedBlockSectors().encodedCells(),
                    Arrays.stream(block.getSectorCounts()).map(short[]::clone).toArray(short[][]::new),
                    new Loop.Outline(row(cells, 1), row(cells, blockSize), PackedRows.column(cells, 1),
                            PackedRows.column(cells, blockSize)),
                    borders(cells)));
            return true;
        }
        Loop.Phase first = recording.phases.getFirst();
        if (first.borders().same(borders(cells)) && Arrays.equals(first.full(), block.getEncodedBlock().encodedCells())) {
            block.hibernate(new Loop(recording.phases, blockSize));
        }
        return false;
    }

    private void wake(Block block) {
        Loop loop = block.getLoop();
        long[][] cells = Packer.readFull(loop.phase().full(), blockSize);
        Loop.Outline received = loop.received();
        cells[0] = received.top().clone();
        cells[blockSize + 1] = received.bottom().clone();
        setColumn(cells, received.left(), 0);
        setColumn(cells, received.right(), blockSize + 1);
        block.wake(cells);
    }

    private Loop.Outline borders(long[][] cells) {
        return new Loop.Outline(row(cells, 0), row(cells, blockSize + 1), PackedRows.column(cells, 0),
                PackedRows.column(cells, blockSize + 1));
    }

    private long[] row(long[][] cells, int x) {
        return cells[x] == emptyRow ? emptyRow : cells[x].clone();
    }

    private static void setColumn(long[][] cells, long[] column, int col) {
        for (int w = 0; w < column.length; w++) {
            for (long rows = column[w]; rows != 0; rows &= rows - 1) {
                PackedRows.set(cells, (w << 6) + Long.numberOfTrailingZeros(rows), col);
            }
        }
    }

    // all cells, the borders too
    private long hash(long[][] cells) {
        long hash = 0;
        for (long[] row : cells) {
            long rowHash = 0;
            if (row != emptyRow) {
                for (int i = 1; i < row.length - 1; i++) rowHash += row[i] * (0x9E3779B97F4A7C15L + 2L * i);
            }
            hash = (hash + rowHash) * 0xBF58476D1CE4E5B9L;
        }
        return hash;
    }

    private static boolean hasCells(short[][] sectorCounts) {
        for (short[] band : sectorCounts) {
            for (short count : band) {
                if (count != 0) return true;
            }
        }
        return false;
    }
}
