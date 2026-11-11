package slobben.cells.service.workers;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import slobben.cells.dto.internal.Columns;
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
 * AI optimized on Blocksize = 2000, sector size = 50.
 * -- below passes the tests
 * Copies the edge cells of every block into the border cells (rows 0 and size + 1, columns 0 and size + 1) of its
 * neighbours, so the GoL sees across block edges. Neighbours that do not exist yet are created.
 * <p>
 * The GoL swaps between the two cell arrays of a block, so the border cells in the cells of a block are those of two
 * ticks ago. Per array the {@link Received} border cells are remembered, so only what differs is written: first every
 * block clears its border rows and finds its edges, then all blocks push their edges, a border column only where it
 * differs from the edge column, and last the border columns that no neighbour pushed to are cleared.
 */
@RequiredArgsConstructor
@Service
public class Stitching extends Worker {
    private static final int PARALLEL_TASKS = 2;
    private static final int PARALLEL_MIN_FULL_READS = 4;
    private static final int PARALLEL_MIN_PUSHES = 256;
    private static final int PRUNE_INTERVAL = 64;

    private final ExecutorService executorService;
    private final Map<String, Block> blocks;
    private final Map<String, Block> newBlocks;
    @Value("${cells.size.blockSize}")
    private int blockSize;
    private long[] innerMask;

    private final IdentityHashMap<Block, State> states = new IdentityHashMap<>();
    private int tick = 0;

    private static final class State {
        final Block[] neighbours = new Block[Direction.values().length];
        final Received[] received = {new Received(), new Received()};
        // of the cells of the block in this tick
        Received current;
        // column 1 and column size, bit x for row x
        long[] left;
        long[] right;
        int tick = Integer.MIN_VALUE;
        boolean topEdge;
        boolean bottomEdge;
        boolean leftEdge;
        boolean rightEdge;

        boolean hasEdges() {
            return topEdge || bottomEdge || leftEdge || rightEdge;
        }

        // what is remembered of this cell array, else the place of the array that was seen longest ago
        Received received(long[][] cells) {
            if (received[0].cells == cells) return received[0];
            if (received[1].cells == cells) return received[1];
            return received[0].tick <= received[1].tick ? received[0] : received[1];
        }
    }

    // the border cells that one cell array of a block got from its neighbours
    private static final class Received {
        // column 0 and column size + 1 as they are in the array, bit x for row x
        long[] left;
        long[] right;
        long[][] cells;
        int tick = Integer.MIN_VALUE;
        boolean top;
        boolean bottom;
        boolean leftPushed;
        boolean rightPushed;
    }

    @PostConstruct
    void init() {
        innerMask = PackedRows.innerMask(blockSize);
    }

    @Override
    public String getName() {
        return "Stitching";
    }

    public void execute() {
        int currentTick = ++tick;
        Block[] blockArray = blocks.values().toArray(new Block[0]);

        if (blockArray.length == 0)
            return;

        State[] blockStates = new State[blockArray.length];
        int fullReads = 0;
        for (int i = 0; i < blockArray.length; i++) {
            Block block = blockArray[i];
            State state = states.computeIfAbsent(block, b -> new State());
            state.tick = currentTick;
            blockStates[i] = state;
            if (!known(block, state)) fullReads++;
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
        if (parallel && pushes(blockStates) >= PARALLEL_MIN_PUSHES) {
            runParallel(blockArray, blockStates, this::push);
        } else {
            for (int i = 0; i < blockArray.length; i++) push(blockArray[i], blockStates[i]);
        }
        for (int i = 0; i < blockArray.length; i++) clearUnpushed(blockArray[i], blockStates[i]);
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

    private static int pushes(State[] blockStates) {
        int pushes = 0;
        for (State state : blockStates) {
            if (state.hasEdges()) pushes++;
        }
        return pushes;
    }

    // the border cells of the cells of the block are still as this worker left them, two ticks ago
    private boolean known(Block block, State state) {
        long[][] cells = block.getPackedCells();
        Received received = state.received(cells);
        return received.cells == cells && received.tick == tick - 2 && block.getBlockState() == BlockState.ACTIVE;
    }

    private void prepare(Block block, State state) {
        if (block.getBlockState() == BlockState.NEW) {
            block.setPackedCells(PackedRows.pack(block.getCells()));
        }
        long[][] cells = block.getPackedCells();
        boolean known = known(block, state);
        Received received = state.received(cells);
        received.cells = cells;
        received.tick = tick;
        received.leftPushed = received.rightPushed = false;
        state.current = received;
        if (!known || received.top) Arrays.fill(cells[0], 0L);
        if (!known || received.bottom) Arrays.fill(cells[blockSize + 1], 0L);
        received.top = received.bottom = false;
        // cells that were not seen two ticks ago: what their border columns hold is read, the pushes fix it
        if (!known) {
            received.left = PackedRows.column(cells, 0);
            received.right = PackedRows.column(cells, blockSize + 1);
        }
        // the edge columns of the GoL, or else from the rows
        Columns edges = block.getEdges();
        boolean stepped = edges != null && edges.of(block);
        state.left = stepped ? edges.left() : PackedRows.column(cells, 1);
        state.right = stepped ? edges.right() : PackedRows.column(cells, blockSize);
        state.topEdge = hasInnerCells(cells[1]);
        state.bottomEdge = hasInnerCells(cells[blockSize]);
        state.leftEdge = hasBits(state.left);
        state.rightEdge = hasBits(state.right);
        block.setBorders(new Columns(cells, block.getGeneration(), received.left, received.right));
    }

    private void push(Block block, State state) {
        if (!state.hasEdges()) return;
        long[][] cells = block.getPackedCells();
        int border = blockSize + 1;
        if (state.topEdge) copyRow(neighbour(block, state, LOW_X_MID_Y), cells[1], border);
        if (state.bottomEdge) copyRow(neighbour(block, state, HIGH_X_MID_Y), cells[blockSize], 0);
        if (state.leftEdge) {
            copyColumn(neighbour(block, state, MID_X_LOW_Y), state.left, border);
            if (bit(state.left, 1)) setCell(neighbour(block, state, LOW_X_LOW_Y), border, border);
            if (bit(state.left, blockSize)) setCell(neighbour(block, state, HIGH_X_LOW_Y), 0, border);
        }
        if (state.rightEdge) {
            copyColumn(neighbour(block, state, MID_X_HIGH_Y), state.right, 0);
            if (bit(state.right, 1)) setCell(neighbour(block, state, LOW_X_HIGH_Y), border, 0);
            if (bit(state.right, blockSize)) setCell(neighbour(block, state, HIGH_X_HIGH_Y), 0, 0);
        }
    }

    // a border column that no neighbour pushed to still has the cells of two ticks ago
    private void clearUnpushed(Block block, State state) {
        Received received = state.current;
        long[][] cells = block.getPackedCells();
        if (!received.leftPushed) clearColumn(cells, received.left, 0);
        if (!received.rightPushed) clearColumn(cells, received.right, blockSize + 1);
    }

    private static void clearColumn(long[][] cells, long[] rows, int column) {
        for (int w = 0; w < rows.length; w++) {
            for (long bits = rows[w]; bits != 0; bits &= bits - 1) {
                PackedRows.clear(cells[(w << 6) + Long.numberOfTrailingZeros(bits)], column);
            }
            rows[w] = 0L;
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
            if (state != null) markReceivedRow(state.current, row);
        }
    }

    private void copyRow(Block target, long[] edge, int row) {
        State state = currentState(target);
        synchronized (target) {
            long[] border = target.getPackedCells()[row];
            for (int i = 0; i < border.length; i++) {
                border[i] |= edge[i] & innerMask[i];
            }
            if (state != null) markReceivedRow(state.current, row);
        }
    }

    private static void markReceivedRow(Received received, int row) {
        if (row == 0) received.top = true;
        else received.bottom = true;
    }

    // makes a border column of the target equal to the edge column: only the cells that differ are written
    private void copyColumn(Block target, long[] edge, int column) {
        State state = currentState(target);
        int word = (column >>> 6) + 1;
        long mask = 1L << column;
        synchronized (target) {
            long[][] cells = target.getPackedCells();
            // a block that was created in this tick has no border cells yet
            long[] border = state == null ? null : column == 0 ? state.current.left : state.current.right;
            for (int w = 0; w < edge.length; w++) {
                long rows = border == null ? edge[w] : edge[w] ^ border[w];
                for (; rows != 0; rows &= rows - 1) {
                    cells[(w << 6) + Long.numberOfTrailingZeros(rows)][word] ^= mask;
                }
                if (border != null) border[w] = edge[w];
            }
            if (state != null && column == 0) state.current.leftPushed = true;
            if (state != null && column != 0) state.current.rightPushed = true;
        }
    }

    private static boolean bit(long[] bits, int i) {
        return (bits[i >>> 6] >>> i & 1L) != 0;
    }

    private static boolean hasBits(long[] bits) {
        for (long word : bits) {
            if (word != 0) return true;
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
