package slobben.cells.service.workers;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import slobben.cells.dto.internal.Coordinates;
import slobben.cells.entities.Block;
import slobben.cells.enums.BlockState;
import slobben.cells.enums.Direction;
import slobben.cells.service.ExecutorService;
import slobben.cells.util.BlockUtils;
import slobben.cells.util.PackedRows;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static slobben.cells.enums.Direction.*;

/**
 * Copies the edge cells of every block into the border cells (rows 0 and size + 1, columns 0 and size + 1) of its
 * neighbours, so the GoL sees across block edges. Neighbours that do not exist yet are created.
 * <p>
 * First every block clears the border cells it got last tick and reads its own edges, then all blocks push their
 * edges. A {@link State} per block remembers which border cells it got, so only those are cleared. Blocks that are new
 * or changed from outside are cleared and read in full; for the others the sector counts of the GoL tell which parts
 * of the edges can hold cells.
 */
@RequiredArgsConstructor
@Service
public class Stitching extends Worker {
    private static final int PARALLEL_TASKS = 2;
    private static final int PARALLEL_MIN_FULL_READS = 4;
    private static final int PARALLEL_MIN_PUSH_WORK = 6000;
    private static final int PUSH_WORK_PER_BLOCK = 16;
    private static final int PRUNE_INTERVAL = 64;

    private final ExecutorService executorService;
    private final Map<String, Block> blocks;
    private final Map<String, Block> newBlocks;
    @Value("${cells.size.blockSize}")
    private int blockSize;
    @Value("${cells.size.sectorSize}")
    private int sectorSize;
    private long[] innerMask;
    private int bitsetWords;
    private int sectors;

    private final IdentityHashMap<Block, State> states = new IdentityHashMap<>();
    private int tick = 0;

    private static final class State {
        // column 1 and column size, bit x - 1 for row x
        final long[] leftColumn;
        final long[] rightColumn;
        // rows whose border columns got cells from a neighbour
        final long[] receivedColumnRows;
        final Block[] neighbours = new Block[Direction.values().length];
        long[][] cells;
        int tick = Integer.MIN_VALUE;
        boolean valid;
        boolean receivedTop;
        boolean receivedBottom;
        boolean topEdge;
        boolean bottomEdge;
        boolean leftEdge;
        boolean rightEdge;

        State(int words) {
            leftColumn = new long[words];
            rightColumn = new long[words];
            receivedColumnRows = new long[words];
        }

        boolean hasEdges() {
            return topEdge || bottomEdge || leftEdge || rightEdge;
        }
    }

    @PostConstruct
    void init() {
        innerMask = PackedRows.innerMask(blockSize);
        bitsetWords = (blockSize + 63) >>> 6;
        sectors = sectorSize > 0 && blockSize % sectorSize == 0 ? blockSize / sectorSize : -1;
    }

    @Override
    public String getName() {
        return "Adding bordercells";
    }

    public void execute() {
        int currentTick = ++tick;
        Block[] blockArray = blocks.values().toArray(new Block[0]);
        if (blockArray.length == 0) return;
        State[] blockStates = new State[blockArray.length];
        int fullReads = 0;
        for (int i = 0; i < blockArray.length; i++) {
            Block block = blockArray[i];
            State state = states.computeIfAbsent(block, b -> new State(bitsetWords));
            // the border cells are still as this worker left them last tick
            state.valid = state.tick == currentTick - 1 && state.cells == block.getPackedCells()
                    && block.getBlockState() == BlockState.ACTIVE;
            state.tick = currentTick;
            blockStates[i] = state;
            if (!state.valid) fullReads++;
        }
        if (states.size() > blockArray.length + 32 || currentTick % PRUNE_INTERVAL == 0) {
            prune(currentTick);
        }

        boolean parallel = blockArray.length >= 2;
        if (parallel && fullReads >= PARALLEL_MIN_FULL_READS) {
            runParallel(blockArray, blockStates, this::prepare);
        } else {
            for (int i = 0; i < blockArray.length; i++) prepare(blockArray[i], blockStates[i]);
        }
        if (parallel && pushWork(blockStates) >= PARALLEL_MIN_PUSH_WORK) {
            runParallel(blockArray, blockStates, this::push);
        } else {
            for (int i = 0; i < blockArray.length; i++) push(blockArray[i], blockStates[i]);
        }
    }

    private void prune(int currentTick) {
        Iterator<State> iterator = states.values().iterator();
        while (iterator.hasNext()) {
            State state = iterator.next();
            if (state.tick != currentTick) {
                iterator.remove();
                continue;
            }
            Block[] neighbours = state.neighbours;
            for (int k = 0; k < neighbours.length; k++) {
                if (neighbours[k] != null && currentState(neighbours[k]) == null) neighbours[k] = null;
            }
        }
    }

    private void runParallel(Block[] blockArray, State[] blockStates, BiConsumer<Block, State> step) {
        AtomicInteger next = new AtomicInteger();
        Set<Runnable> tasks = HashSet.newHashSet(PARALLEL_TASKS);
        for (int t = 0; t < PARALLEL_TASKS; t++) {
            tasks.add(() -> {
                for (int i = next.getAndIncrement(); i < blockArray.length; i = next.getAndIncrement()) {
                    step.accept(blockArray[i], blockStates[i]);
                }
            });
        }
        executorService.executeTasksParallel(tasks, getName());
    }

    private static long pushWork(State[] blockStates) {
        long work = 0;
        for (int i = 0; i < blockStates.length && work < PARALLEL_MIN_PUSH_WORK; i++) {
            State state = blockStates[i];
            if (!state.hasEdges()) continue;
            if (state.leftEdge) work += bitCount(state.leftColumn);
            if (state.rightEdge) work += bitCount(state.rightColumn);
            work += PUSH_WORK_PER_BLOCK;
        }
        return work;
    }

    private void prepare(Block block, State state) {
        boolean isNew = block.getBlockState() == BlockState.NEW;
        if (isNew) {
            block.setPackedCells(PackedRows.pack(block.getCells()));
        }
        long[][] cells = block.getPackedCells();
        state.cells = cells;
        boolean known = state.valid && !isNew;
        if (!known || state.receivedTop) Arrays.fill(cells[0], 0L);
        if (!known || state.receivedBottom) Arrays.fill(cells[blockSize + 1], 0L);
        state.receivedTop = false;
        state.receivedBottom = false;
        Arrays.fill(state.leftColumn, 0L);
        Arrays.fill(state.rightColumn, 0L);

        short[][] counts = known ? exactSectorCounts(block) : null;
        if (counts == null) {
            readEdges(cells, state, 1, blockSize + 1, true, true, !known);
        } else {
            int last = counts.length - 1;
            for (int band = 0; band <= last; band++) {
                boolean left = counts[band][0] != 0;
                boolean right = counts[band][last] != 0;
                if (left || right) {
                    readEdges(cells, state, band * sectorSize + 1, (band + 1) * sectorSize + 1, left, right, false);
                }
            }
        }
        if (known) {
            clearReceivedColumns(cells, state.receivedColumnRows);
        } else {
            Arrays.fill(state.receivedColumnRows, 0L);
        }

        state.topEdge = (counts == null || hasCount(counts[0])) && hasInnerCells(cells[1]);
        state.bottomEdge = (counts == null || hasCount(counts[counts.length - 1])) && hasInnerCells(cells[blockSize]);
        state.leftEdge = hasBits(state.leftColumn);
        state.rightEdge = hasBits(state.rightColumn);
    }

    private short[][] exactSectorCounts(Block block) {
        short[][] counts = block.getSectorCounts();
        if (counts == null || counts.length != sectors) return null;
        for (short[] row : counts) {
            if (row == null || row.length != sectors) return null;
        }
        return counts;
    }

    private void readEdges(long[][] cells, State state, int from, int to, boolean left, boolean right,
                           boolean clearBorder) {
        for (int x = from; x < to; x++) {
            long[] row = cells[x];
            int bit = x - 1;
            if (left) state.leftColumn[bit >>> 6] |= cell(row, 1) << bit;
            if (right) state.rightColumn[bit >>> 6] |= cell(row, blockSize) << bit;
            if (clearBorder) {
                clear(row, 0);
                clear(row, blockSize + 1);
            }
        }
    }

    private void clearReceivedColumns(long[][] cells, long[] rows) {
        for (int w = 0; w < rows.length; w++) {
            for (long bits = rows[w]; bits != 0; bits &= bits - 1) {
                long[] row = cells[(w << 6) + Long.numberOfTrailingZeros(bits) + 1];
                clear(row, 0);
                clear(row, blockSize + 1);
            }
            rows[w] = 0L;
        }
    }

    private void push(Block block, State state) {
        if (!state.hasEdges()) return;
        long[][] cells = block.getPackedCells();
        int border = blockSize + 1;
        int lastBit = blockSize - 1;
        if (state.topEdge) copyRow(neighbour(block, state, LOW_X_MID_Y), cells[1], border);
        if (state.bottomEdge) copyRow(neighbour(block, state, HIGH_X_MID_Y), cells[blockSize], 0);
        if (state.leftEdge) {
            copyColumn(neighbour(block, state, MID_X_LOW_Y), state.leftColumn, border);
            if (bit(state.leftColumn, 0)) setCell(neighbour(block, state, LOW_X_LOW_Y), border, border);
            if (bit(state.leftColumn, lastBit)) setCell(neighbour(block, state, HIGH_X_LOW_Y), 0, border);
        }
        if (state.rightEdge) {
            copyColumn(neighbour(block, state, MID_X_HIGH_Y), state.rightColumn, 0);
            if (bit(state.rightColumn, 0)) setCell(neighbour(block, state, LOW_X_HIGH_Y), border, 0);
            if (bit(state.rightColumn, lastBit)) setCell(neighbour(block, state, HIGH_X_HIGH_Y), 0, 0);
        }
    }

    private Block neighbour(Block block, State state, Direction direction) {
        Block cached = state.neighbours[direction.ordinal()];
        if (cached != null && currentState(cached) != null) return cached;
        String key = BlockUtils.getKey(block.getX() + direction.getDx(), block.getY() + direction.getDy());
        Block neighbour = blocks.get(key);
        if (neighbour == null) {
            neighbour = newBlocks.computeIfAbsent(key, k -> {
                Coordinates coordinates = BlockUtils.resolveKey(k);
                return new Block(coordinates.x(), coordinates.y(), block.getResponsibleChaosHit(), blockSize);
            });
        }
        state.neighbours[direction.ordinal()] = neighbour;
        return neighbour;
    }

    private State currentState(Block block) {
        State state = states.get(block);
        return state != null && state.tick == tick ? state : null;
    }

    private void setCell(Block target, int row, int column) {
        State state = currentState(target);
        synchronized (target) {
            PackedRows.set(target.getPackedCells()[row], column);
            if (state != null) markReceivedRow(state, row);
        }
    }

    private void copyRow(Block target, long[] edge, int row) {
        State state = currentState(target);
        synchronized (target) {
            long[] border = target.getPackedCells()[row];
            for (int i = 0; i < border.length; i++) {
                border[i] |= edge[i] & innerMask[i];
            }
            if (state != null) markReceivedRow(state, row);
        }
    }

    private static void markReceivedRow(State state, int row) {
        if (row == 0) state.receivedTop = true;
        else state.receivedBottom = true;
    }

    private void copyColumn(Block target, long[] edge, int column) {
        State state = currentState(target);
        int word = (column >>> 6) + 1;
        long mask = 1L << column;
        synchronized (target) {
            long[][] cells = target.getPackedCells();
            for (int w = 0; w < edge.length; w++) {
                long rows = edge[w];
                if (rows == 0) continue;
                if (state != null) state.receivedColumnRows[w] |= rows;
                for (; rows != 0; rows &= rows - 1) {
                    cells[(w << 6) + Long.numberOfTrailingZeros(rows) + 1][word] |= mask;
                }
            }
        }
    }

    private static long cell(long[] row, int column) {
        return row[(column >>> 6) + 1] >>> column & 1L;
    }

    // writes only when the bit is set, so rows without border cells are not touched
    private static void clear(long[] row, int column) {
        int word = (column >>> 6) + 1;
        long mask = 1L << column;
        if ((row[word] & mask) != 0) row[word] &= ~mask;
    }

    private static boolean bit(long[] bits, int i) {
        return (bits[i >>> 6] >>> i & 1L) != 0;
    }

    private static long bitCount(long[] bits) {
        long count = 0;
        for (long word : bits) count += Long.bitCount(word);
        return count;
    }

    private static boolean hasBits(long[] bits) {
        for (long word : bits) {
            if (word != 0) return true;
        }
        return false;
    }

    private static boolean hasCount(short[] counts) {
        for (short count : counts) {
            if (count != 0) return true;
        }
        return false;
    }

    private boolean hasInnerCells(long[] row) {
        for (int i = 0; i < row.length; i++) {
            if ((row[i] & innerMask[i]) != 0) return true;
        }
        return false;
    }
}
