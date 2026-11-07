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

import static slobben.cells.util.BlockUtils.getKey;

@RequiredArgsConstructor
@Service
public class Stitching extends Worker {
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final int PAR_TASKS = 2;
    private static final int PAR_FULL_SCANS = 4;
    private static final int PUSH_INLINE_BITS = 6000;
    private static final int PRUNE_INTERVAL = 64;

    private static final int F_LOW_ROW = 1;
    private static final int F_HIGH_ROW = 2;
    private static final int F_LEFT = 4;
    private static final int F_RIGHT = 8;

    private final ExecutorService executorService;
    private final Map<String, Block> blocks;
    private final Map<String, Block> newBlocks;
    @Value("${cells.size.blockSize}")
    private int blockSize;
    @Value("${cells.size.sectorSize}")
    private int sectorSize;
    private long[] innerMask;

    private int w0, wMax, w1, wB;
    private long m0, mMax, m1, mB;
    private int s1, sB;
    private int bitWords;
    private boolean fastLayout;
    private int nSectors;

    private final IdentityHashMap<Block, State> states = new IdentityHashMap<>();
    private Block[] arrBuf = new Block[0];
    private State[] stBuf = new State[0];
    private int lastN = 0;
    private int exec = 0;

    private static final class State {
        final long[] left;
        final long[] right;
        final long[] track;
        final Block[] nb;
        long[][] packed;
        int stamp = Integer.MIN_VALUE;
        boolean valid;
        boolean row0;
        boolean rowMax;
        int flags;

        State(int words, int dirs) {
            left = new long[words];
            right = new long[words];
            track = new long[words];
            nb = new Block[dirs];
        }
    }

    @PostConstruct
    void init() {
        innerMask = PackedRows.innerMask(blockSize);
        int rowLen = PackedRows.create(blockSize)[0].length;
        int max = blockSize + 1;
        long[] p;
        p = probe(rowLen, 0);
        w0 = (int) p[0];
        m0 = p[1];
        p = probe(rowLen, max);
        wMax = (int) p[0];
        mMax = p[1];
        p = probe(rowLen, 1);
        w1 = (int) p[0];
        m1 = p[1];
        p = probe(rowLen, blockSize);
        wB = (int) p[0];
        mB = p[1];
        s1 = Long.numberOfTrailingZeros(m1);
        sB = Long.numberOfTrailingZeros(mB);
        bitWords = (blockSize + 63) >>> 6;
        fastLayout = (w0 == w1) && (wB == wMax);
        nSectors = (sectorSize > 0 && blockSize % sectorSize == 0) ? blockSize / sectorSize : -1;
    }

    private static long[] probe(int rowLen, int col) {
        long[] r = new long[rowLen];
        PackedRows.set(r, col);
        for (int i = 0; i < rowLen; i++) {
            if (r[i] != 0) return new long[]{i, r[i]};
        }
        throw new IllegalStateException("Could not probe packed layout for column " + col);
    }

    @Override
    public String getName() {
        return "Adding bordercells";
    }

    public void execute() {
        final int ex = ++exec;
        Block[] arr = arrBuf;
        State[] st = stBuf;
        int n = 0;
        for (Block b : blocks.values()) {
            if (n == arr.length) {
                int cap = Math.max(16, n * 2);
                arr = Arrays.copyOf(arr, cap);
                st = Arrays.copyOf(st, cap);
            }
            arr[n++] = b;
        }
        if (n < lastN) {
            Arrays.fill(arr, n, lastN, null);
            Arrays.fill(st, n, lastN, null);
        }
        lastN = n;
        arrBuf = arr;
        stBuf = st;
        if (n == 0) return;

        final int words = bitWords;
        final int dirs = DIRECTIONS.length;
        int full = 0;
        for (int i = 0; i < n; i++) {
            Block b = arr[i];
            State s = states.get(b);
            if (s == null) {
                s = new State(words, dirs);
                states.put(b, s);
            }
            boolean v = s.stamp == ex - 1 && s.packed == b.getPackedCells()
                    && b.getBlockState() == BlockState.ACTIVE;
            s.valid = v;
            s.stamp = ex;
            st[i] = s;
            if (!v) full++;
        }
        if (states.size() > n + 32 || (ex & (PRUNE_INTERVAL - 1)) == 0) {
            prune(ex);
        }

        if (full >= PAR_FULL_SCANS && n >= 2) {
            runParallel(n, arr, st, true);
        } else {
            for (int i = 0; i < n; i++) prepare(arr[i], st[i]);
        }

        long work = 0;
        for (int i = 0; i < n && work < PUSH_INLINE_BITS; i++) {
            State s = st[i];
            int f = s.flags;
            if (f == 0) continue;
            if ((f & F_LEFT) != 0) for (long v : s.left) work += Long.bitCount(v);
            if ((f & F_RIGHT) != 0) for (long v : s.right) work += Long.bitCount(v);
            work += 16;
        }

        if (work < PUSH_INLINE_BITS || n < 2) {
            for (int i = 0; i < n; i++) {
                State s = st[i];
                if (s.flags != 0) addBorderCells(arr[i], s);
            }
        } else {
            runParallel(n, arr, st, false);
        }
    }

    private void prune(int ex) {
        Iterator<State> it = states.values().iterator();
        while (it.hasNext()) {
            State s = it.next();
            if (s.stamp != ex) {
                it.remove();
                continue;
            }
            Block[] nb = s.nb;
            for (int k = 0; k < nb.length; k++) {
                Block c = nb[k];
                if (c != null) {
                    State cs = states.get(c);
                    if (cs == null || cs.stamp != ex) nb[k] = null;
                }
            }
        }
    }

    private void runParallel(int n, Block[] arr, State[] st, boolean prep) {
        final AtomicInteger ctr = new AtomicInteger();
        Set<Runnable> tasks = HashSet.newHashSet(PAR_TASKS * 2);
        for (int t = 0; t < PAR_TASKS; t++) {
            tasks.add(() -> {
                int i;
                while ((i = ctr.getAndIncrement()) < n) {
                    if (prep) {
                        prepare(arr[i], st[i]);
                    } else if (st[i].flags != 0) {
                        addBorderCells(arr[i], st[i]);
                    }
                }
            });
        }
        executorService.executeTasksParallel(tasks, getName());
    }

    private static boolean sectorsOk(short[][] sc, int ns) {
        if (ns <= 0 || sc == null || sc.length != ns) return false;
        for (int i = 0; i < ns; i++) {
            if (sc[i] == null || sc[i].length != ns) return false;
        }
        return true;
    }

    // clears bits [from, to) in t
    private static void clearBits(long[] t, int from, int to) {
        if (from >= to) return;
        int fw = from >>> 6;
        int lw = (to - 1) >>> 6;
        long fm = -1L << (from & 63);
        long lm = -1L >>> (63 - ((to - 1) & 63));
        if (fw == lw) {
            t[fw] &= ~(fm & lm);
        } else {
            t[fw] &= ~fm;
            for (int w = fw + 1; w < lw; w++) t[w] = 0L;
            t[lw] &= ~lm;
        }
    }

    private void prepare(Block block, State s) {
        final BlockState state = block.getBlockState();
        final boolean isNew = state == BlockState.NEW;
        if (isNew) {
            block.setPackedCells(PackedRows.pack(block.getCells()));
        }
        final long[][] packed = block.getPackedCells();
        s.packed = packed;
        final int max = blockSize + 1;
        final boolean valid = s.valid && !isNew;
        if (!valid || s.row0) Arrays.fill(packed[0], 0L);
        if (!valid || s.rowMax) Arrays.fill(packed[max], 0L);
        s.row0 = false;
        s.rowMax = false;
        final int words = bitWords;
        final long[] left = s.left;
        final long[] right = s.right;
        final long[] track = s.track;
        long anyL = 0, anyR = 0;
        boolean checkLow = true, checkHigh = true;

        if (valid) {
            Arrays.fill(left, 0L);
            Arrays.fill(right, 0L);
            short[][] sc = block.getSectorCounts();
            int ns = nSectors;
            if (sectorsOk(sc, ns)) {
                final int ss = sectorSize;
                final int last = ns - 1;
                final boolean fl = fastLayout;
                boolean lowAny = false, highAny = false;
                for (int i = 0; i < ns; i++) {
                    boolean l = sc[i][0] != 0 || sc[0][i] != 0;
                    boolean r = sc[i][last] != 0 || sc[last][i] != 0;
                    lowAny |= l;
                    highAny |= r;
                    if (l || r) {
                        int xs = i * ss + 1;
                        if (fl && l && r) {
                            extractClear(packed, xs, xs + ss, left, right);
                            clearBits(track, xs - 1, xs - 1 + ss);
                        } else {
                            extract(packed, xs, xs + ss, left, right, l, r);
                        }
                    }
                }
                checkLow = lowAny;
                checkHigh = highAny;
            } else {
                extract(packed, 1, max, left, right, true, true);
            }
            final int lw0 = w0, lwMax = wMax;
            final long nm0 = ~m0, nmMax = ~mMax;
            for (int w = 0; w < words; w++) {
                long word = track[w];
                if (word == 0) continue;
                int base = (w << 6) + 1;
                while (word != 0) {
                    int t = Long.numberOfTrailingZeros(word);
                    long[] r = packed[base + t];
                    r[lw0] &= nm0;
                    r[lwMax] &= nmMax;
                    word &= word - 1;
                }
                track[w] = 0L;
            }
            for (int w = 0; w < words; w++) {
                anyL |= left[w];
                anyR |= right[w];
            }
        } else {
            Arrays.fill(track, 0L);
            final int lw0 = w0, lwMax = wMax, lw1 = w1, lwB = wB;
            final long lm0 = m0, lmMax = mMax, nm0 = ~m0, nmMax = ~mMax, lm1 = m1, lmB = mB;
            final int ls1 = s1, lsB = sB;
            if (fastLayout) {
                for (int w = 0; w < words; w++) {
                    int xs = (w << 6) + 1;
                    int xe = Math.min(xs + 64, max);
                    long accL = 0, accR = 0;
                    for (int x = xs, b = 0; x < xe; x++, b++) {
                        long[] r = packed[x];
                        long a = r[lw0];
                        accL |= ((a >>> ls1) & 1L) << b;
                        if ((a & lm0) != 0) r[lw0] = a & nm0;
                        long c = r[lwB];
                        accR |= ((c >>> lsB) & 1L) << b;
                        if ((c & lmMax) != 0) r[lwB] = c & nmMax;
                    }
                    left[w] = accL;
                    right[w] = accR;
                    anyL |= accL;
                    anyR |= accR;
                }
            } else {
                for (int w = 0; w < words; w++) {
                    int xs = (w << 6) + 1;
                    int xe = Math.min(xs + 64, max);
                    long accL = 0, accR = 0;
                    for (int x = xs, b = 0; x < xe; x++, b++) {
                        long[] r = packed[x];
                        r[lw0] &= nm0;
                        r[lwMax] &= nmMax;
                        accL |= ((r[lw1] & lm1) >>> ls1) << b;
                        accR |= ((r[lwB] & lmB) >>> lsB) << b;
                    }
                    left[w] = accL;
                    right[w] = accR;
                    anyL |= accL;
                    anyR |= accR;
                }
            }
        }
        int f = 0;
        if (checkLow && rowAny(packed[1])) f |= F_LOW_ROW;
        if (checkHigh && rowAny(packed[blockSize])) f |= F_HIGH_ROW;
        if (anyL != 0) f |= F_LEFT;
        if (anyR != 0) f |= F_RIGHT;
        s.flags = f;
    }

    // fastLayout only: col0/col1 share a word, colB/colMax share a word
    private void extractClear(long[][] packed, int xs, int xe, long[] left, long[] right) {
        final int lw0 = w0, lwB = wB, ls1 = s1, lsB = sB;
        final long lm0 = m0, lmMax = mMax, nm0 = ~m0, nmMax = ~mMax;
        for (int x = xs; x < xe; x++) {
            long[] r = packed[x];
            int b = x - 1;
            int wi = b >>> 6;
            int sh = b & 63;
            long a = r[lw0];
            left[wi] |= ((a >>> ls1) & 1L) << sh;
            if ((a & lm0) != 0) r[lw0] = a & nm0;
            long c = r[lwB];
            right[wi] |= ((c >>> lsB) & 1L) << sh;
            if ((c & lmMax) != 0) r[lwB] = c & nmMax;
        }
    }

    private void extract(long[][] packed, int xs, int xe, long[] left, long[] right, boolean doL, boolean doR) {
        final int lw1 = w1, lwB = wB, ls1 = s1, lsB = sB;
        if (doL && doR) {
            for (int x = xs; x < xe; x++) {
                long[] r = packed[x];
                int b = x - 1;
                int wi = b >>> 6;
                int sh = b & 63;
                left[wi] |= ((r[lw1] >>> ls1) & 1L) << sh;
                right[wi] |= ((r[lwB] >>> lsB) & 1L) << sh;
            }
        } else if (doL) {
            for (int x = xs; x < xe; x++) {
                int b = x - 1;
                left[b >>> 6] |= ((packed[x][lw1] >>> ls1) & 1L) << (b & 63);
            }
        } else if (doR) {
            for (int x = xs; x < xe; x++) {
                int b = x - 1;
                right[b >>> 6] |= ((packed[x][lwB] >>> lsB) & 1L) << (b & 63);
            }
        }
    }

    private boolean rowAny(long[] row) {
        long any = 0;
        long[] mask = innerMask;
        for (int i = 0; i < row.length; i++) {
            any |= row[i] & mask[i];
        }
        return any != 0;
    }

    private static boolean bit(long[] bits, int i) {
        return ((bits[i >>> 6] >>> (i & 63)) & 1L) != 0;
    }

    private void addBorderCells(Block block, State s) {
        final int f = s.flags;
        if (f == 0) return;
        final long[] left = s.left;
        final long[] right = s.right;
        long[][] packed = block.getPackedCells();
        UUID hit = block.getResponsibleChaosHit();
        int max = blockSize + 1;
        int bx = block.getX();
        int by = block.getY();
        boolean hasLeft = (f & F_LEFT) != 0;
        boolean hasRight = (f & F_RIGHT) != 0;
        for (Direction direction : DIRECTIONS) {
            switch (direction) {
                case LOW_X_LOW_Y -> {
                    if (hasLeft && bit(left, 0)) setCell(target(s, bx, by, direction, hit), max, max);
                }
                case LOW_X_MID_Y -> {
                    if ((f & F_LOW_ROW) != 0) copyRow(target(s, bx, by, direction, hit), packed[1], max);
                }
                case LOW_X_HIGH_Y -> {
                    if (hasRight && bit(right, 0)) setCell(target(s, bx, by, direction, hit), max, 0);
                }
                case MID_X_LOW_Y -> {
                    if (hasLeft) copyColumn(target(s, bx, by, direction, hit), left, wMax, mMax);
                }
                case MID_X_HIGH_Y -> {
                    if (hasRight) copyColumn(target(s, bx, by, direction, hit), right, w0, m0);
                }
                case HIGH_X_LOW_Y -> {
                    if (hasLeft && bit(left, blockSize - 1)) setCell(target(s, bx, by, direction, hit), 0, max);
                }
                case HIGH_X_MID_Y -> {
                    if ((f & F_HIGH_ROW) != 0) copyRow(target(s, bx, by, direction, hit), packed[blockSize], 0);
                }
                case HIGH_X_HIGH_Y -> {
                    if (hasRight && bit(right, blockSize - 1)) setCell(target(s, bx, by, direction, hit), 0, 0);
                }
            }
        }
    }

    private Block target(State s, int bx, int by, Direction direction, UUID hit) {
        final int ord = direction.ordinal();
        Block cached = s.nb[ord];
        if (cached != null) {
            State cs = states.get(cached);
            if (cs != null && cs.stamp == exec) return cached;
        }
        String key = getKey(bx + direction.getDx(), by + direction.getDy());
        Block block = blocks.get(key);
        if (block == null) {
            block = newBlocks.computeIfAbsent(key, k -> {
                Coordinates coordinates = BlockUtils.resolveKey(k);
                return new Block(coordinates.x(), coordinates.y(), hit, blockSize);
            });
        }
        s.nb[ord] = block;
        return block;
    }

    private State currentState(Block b) {
        State ts = states.get(b);
        return (ts != null && ts.stamp == exec) ? ts : null;
    }

    private void setCell(Block b, int dstRow, int dstCol) {
        State ts = currentState(b);
        synchronized (b) {
            PackedRows.set(b.getPackedCells()[dstRow], dstCol);
            if (ts != null) {
                if (dstRow == 0) ts.row0 = true;
                else ts.rowMax = true;
            }
        }
    }

    private void copyRow(Block b, long[] srcRow, int dstRow) {
        long[] mask = innerMask;
        State ts = currentState(b);
        synchronized (b) {
            long[] dst = b.getPackedCells()[dstRow];
            for (int i = 0; i < dst.length; i++) {
                dst[i] |= srcRow[i] & mask[i];
            }
            if (ts != null) {
                if (dstRow == 0) ts.row0 = true;
                else ts.rowMax = true;
            }
        }
    }

    private void copyColumn(Block b, long[] bits, int wDst, long mDst) {
        State ts = currentState(b);
        synchronized (b) {
            long[][] dst = b.getPackedCells();
            long[] track = ts != null ? ts.track : null;
            for (int w = 0; w < bits.length; w++) {
                long word = bits[w];
                if (word == 0) continue;
                if (track != null) track[w] |= word;
                int base = (w << 6) + 1;
                while (word != 0) {
                    int t = Long.numberOfTrailingZeros(word);
                    dst[base + t][wDst] |= mDst;
                    word &= word - 1;
                }
            }
        }
    }
}
