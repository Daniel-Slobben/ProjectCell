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
 * Bit-parallel Game of Life. Horizontal partial sums are computed once per input row and kept in a ring,
 * two output rows are produced per pass, sector counts are maintained with popcounts per sector segment.
 * Per row pair only the word range covering non-zero input words is processed.
 * Blocks that carry a large share of the total work are split into horizontal strips (aligned to sector bands)
 * processed in parallel, using copies of the strip boundary rows.
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
    private int[] segStart;
    private long[] segMask;
    private int[] segSector;
    private boolean seg3;
    private long[] sm0;
    private long[] sm1;
    private long[] sm2;
    private int[] sk0;
    private int[] sk1;
    private int[] sk2;

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
        segStart = new int[words + 2];
        List<Long> masks = new ArrayList<>();
        List<Integer> sectors = new ArrayList<>();
        for (int i = 1; i <= words; i++) {
            segStart[i] = masks.size();
            long m = innerMask[i];
            int base = (i - 1) << 6;
            int curSector = -1;
            long curMask = 0L;
            while (m != 0) {
                int bit = Long.numberOfTrailingZeros(m);
                int sec = columnSector[base + bit];
                if (sec != curSector) {
                    if (curMask != 0L) {
                        masks.add(curMask);
                        sectors.add(curSector);
                    }
                    curSector = sec;
                    curMask = 0L;
                }
                curMask |= 1L << bit;
                m &= m - 1;
            }
            if (curMask != 0L) {
                masks.add(curMask);
                sectors.add(curSector);
            }
        }
        segStart[words + 1] = masks.size();
        segMask = new long[masks.size()];
        segSector = new int[sectors.size()];
        for (int s = 0; s < segMask.length; s++) {
            segMask[s] = masks.get(s);
            segSector[s] = sectors.get(s);
        }
        int maxSeg = 0;
        sm0 = new long[words + 2];
        sm1 = new long[words + 2];
        sm2 = new long[words + 2];
        sk0 = new int[words + 2];
        sk1 = new int[words + 2];
        sk2 = new int[words + 2];
        for (int i = 1; i <= words; i++) {
            int base = segStart[i];
            int n = segStart[i + 1] - base;
            maxSeg = Math.max(maxSeg, n);
            if (n > 0) {
                sm0[i] = segMask[base];
                sk0[i] = segSector[base];
            }
            if (n > 1) {
                sm1[i] = segMask[base + 1];
                sk1[i] = segSector[base + 1];
            } else {
                sk1[i] = sk0[i];
            }
            if (n > 2) {
                sm2[i] = segMask[base + 2];
                sk2[i] = segSector[base + 2];
            } else {
                sk2[i] = sk1[i];
            }
        }
        seg3 = maxSeg <= 3;
    }

    @Override
    public String getName() {
        return "GOL Algorithm";
    }

    @Override
    public void execute() {
        Set<Runnable> tasks = new HashSet<>();
        List<Block> active = new ArrayList<>();
        List<Integer> lives = new ArrayList<>();
        long totalLive = 0;
        for (Block block : blocks.values()) {
            if (block.getBlockState() == BlockState.HIBERNATION) {
                block.setNextHibernationState();
                continue;
            }
            if (block.getSectorCounts() == null) {
                setInitialSectorHeat(block);
            }
            int live = liveCount(block.getSectorCounts());
            active.add(block);
            lives.add(live);
            totalLive += live;
        }
        if (active.isEmpty()) {
            return;
        }
        final int size = blockSize;
        long target = Math.max(12000L, totalLive / 8);
        List<Block> splitBlocks = new ArrayList<>();
        for (int n = 0; n < active.size(); n++) {
            final Block block = active.get(n);
            final short[][] sc = block.getSectorCounts();
            final long[][] packed = block.getPackedCells();
            int live = lives.get(n);
            int strips = (int) Math.min(4L, (live + target - 1) / target);
            strips = Math.min(strips, totalSectors);
            if (strips <= 1) {
                tasks.add(() -> {
                    processRows(packed, sc, 1, size, packed[0], packed[size + 1]);
                    block.blockUpdated();
                });
                continue;
            }
            for (int j = 0; j < strips; j++) {
                final int a = 1 + (totalSectors * j / strips) * sectorSize;
                final int b = (totalSectors * (j + 1) / strips) * sectorSize;
                final long[] up = (j == 0) ? packed[0] : packed[a - 1].clone();
                final long[] down = (j == strips - 1) ? packed[size + 1] : packed[b + 1].clone();
                tasks.add(() -> processRows(packed, sc, a, b, up, down));
            }
            splitBlocks.add(block);
        }
        executorService.executeTasksParallel(tasks, getName());
        for (Block block : splitBlocks) {
            block.blockUpdated();
        }
    }

    private static int liveCount(short[][] sc) {
        int live = 0;
        for (short[] r : sc) {
            for (short v : r) {
                live += v;
            }
        }
        return live;
    }

    private static int firstNZ(long[] row, int w) {
        for (int i = 0; i <= w + 1; i++) {
            if (row[i] != 0L) return i;
        }
        return w + 2;
    }

    private static int lastNZ(long[] row, int w, int first) {
        if (first > w + 1) return -1;
        for (int i = w + 1; i > first; i--) {
            if (row[i] != 0L) return i;
        }
        return first;
    }

    public void setNextState(Block block) {
        if (block.getBlockState() == BlockState.HIBERNATION) {
            block.setNextHibernationState();
            return;
        }
        if (block.getSectorCounts() == null) {
            setInitialSectorHeat(block);
        }
        long[][] packed = block.getPackedCells();
        processRows(packed, block.getSectorCounts(), 1, blockSize, packed[0], packed[blockSize + 1]);
        block.blockUpdated();
    }

    private void processRows(long[][] packed, short[][] sectorCounts, int a, int b, long[] up, long[] down) {
        final int w = words;
        final long[] inner = innerMask;
        final int sSize = sectorSize;

        long[] pO = new long[w + 2];
        long[] pT = new long[w + 2];
        long[] cO = new long[w + 2];
        long[] cT = new long[w + 2];
        long[] rowA = up;
        long[] rowB = packed[a];
        rowSums(rowA, pO, pT);
        rowSums(rowB, cO, cT);

        int fA = firstNZ(rowA, w);
        int lA = lastNZ(rowA, w, fA);
        int fB = firstNZ(rowB, w);
        int lB = lastNZ(rowB, w, fB);

        int x = a;
        for (; x + 1 <= b; x += 2) {
            long[] r0 = packed[x];
            long[] r1 = packed[x + 1];
            long[] r2 = (x + 2 <= b) ? packed[x + 2] : down;
            int fC = firstNZ(r1, w);
            int lC = lastNZ(r1, w, fC);
            int fD = firstNZ(r2, w);
            int lD = lastNZ(r2, w, fD);
            int lo = Math.min(Math.min(fA, fB), Math.min(fC, fD));
            int hi = Math.max(Math.max(lA, lB), Math.max(lC, lD));
            fA = fC;
            lA = lC;
            fB = fD;
            lB = lD;
            int L = Math.max(1, lo - 1);
            int H = Math.min(w, hi + 1);
            if (L > H) {
                continue;
            }
            short[] s0 = sectorCounts[(x - 1) / sSize];
            short[] s1 = sectorCounts[x / sSize];
            final boolean same = s0 == s1;
            long a1Prev = r1[L - 1];
            long a1Cur = r1[L];
            long a2Prev = r2[L - 1];
            long a2Cur = r2[L];
            for (int i = L; i <= H; i++) {
                long a1Next = r1[i + 1];
                long a2Next = r2[i + 1];

                long l1 = (a1Cur << 1) | (a1Prev >>> 63);
                long rr1 = (a1Cur >>> 1) | (a1Next << 63);
                long x1 = l1 ^ a1Cur;
                long aO = x1 ^ rr1;
                long aT = (l1 & a1Cur) | (x1 & rr1);

                long l2 = (a2Cur << 1) | (a2Prev >>> 63);
                long rr2 = (a2Cur >>> 1) | (a2Next << 63);
                long x2 = l2 ^ a2Cur;
                long bO = x2 ^ rr2;
                long bT = (l2 & a2Cur) | (x2 & rr2);

                long uO = pO[i];
                long uT = pT[i];
                long mO3 = cO[i];
                long mT3 = cT[i];
                pO[i] = aO;
                pT[i] = aT;
                cO[i] = bO;
                cT[i] = bT;

                long m1 = a1Cur;
                a1Prev = a1Cur;
                a1Cur = a1Next;
                a2Prev = a2Cur;
                a2Cur = a2Next;

                if ((uO | uT | mO3 | mT3 | aO | aT | bO | bT) == 0L) {
                    continue;
                }

                long m0 = r0[i];
                long mask = inner[i];

                long mO = mO3 ^ m0;
                long mT = mT3 & ~(m0 & ~mO3);
                long e = uO ^ mO;
                long o = e ^ aO;
                long c = (uO & mO) | (e & aO);
                long p = uT ^ mT;
                long q = uT & mT;
                long r = aT ^ c;
                long t = aT & c;
                long next0 = ((p ^ r) & ~(q | t)) & (o | m0);
                long ch0 = (next0 ^ m0) & mask;

                long nO = aO ^ m1;
                long nT = aT & ~(m1 & ~aO);
                long e1 = mO3 ^ nO;
                long o1 = e1 ^ bO;
                long c1 = (mO3 & nO) | (e1 & bO);
                long p1 = mT3 ^ nT;
                long q1 = mT3 & nT;
                long rq = bT ^ c1;
                long t1 = bT & c1;
                long next1 = ((p1 ^ rq) & ~(q1 | t1)) & (o1 | m1);
                long ch1 = (next1 ^ m1) & mask;

                if ((ch0 | ch1) != 0L) {
                    if (ch0 != 0L) {
                        r0[i] = m0 ^ ch0;
                    }
                    if (ch1 != 0L) {
                        r1[i] = m1 ^ ch1;
                    }
                    long b0 = ch0 & next0;
                    long d0 = ch0 & m0;
                    long b1 = ch1 & next1;
                    long d1 = ch1 & m1;
                    if (same) {
                        sectorDelta2(s0, i, b0, d0, b1, d1);
                    } else {
                        sectorDelta(s0, i, b0, d0);
                        sectorDelta(s1, i, b1, d1);
                    }
                }
            }
        }
        if (x == b) {
            long[] r0 = packed[x];
            long[] r1 = down;
            int fC = firstNZ(r1, w);
            int lC = lastNZ(r1, w, fC);
            int lo = Math.min(Math.min(fA, fB), fC);
            int hi = Math.max(Math.max(lA, lB), lC);
            int L = Math.max(1, lo - 1);
            int H = Math.min(w, hi + 1);
            if (L <= H) {
                short[] s0 = sectorCounts[(x - 1) / sSize];
                long a1Prev = r1[L - 1];
                long a1Cur = r1[L];
                for (int i = L; i <= H; i++) {
                    long a1Next = r1[i + 1];
                    long l1 = (a1Cur << 1) | (a1Prev >>> 63);
                    long rr1 = (a1Cur >>> 1) | (a1Next << 63);
                    long x1 = l1 ^ a1Cur;
                    long aO = x1 ^ rr1;
                    long aT = (l1 & a1Cur) | (x1 & rr1);
                    a1Prev = a1Cur;
                    a1Cur = a1Next;

                    long uO = pO[i];
                    long uT = pT[i];
                    long mO3 = cO[i];
                    long mT3 = cT[i];
                    if ((uO | uT | mO3 | mT3 | aO | aT) == 0L) {
                        continue;
                    }
                    long m0 = r0[i];
                    long mO = mO3 ^ m0;
                    long mT = mT3 & ~(m0 & ~mO3);
                    long e = uO ^ mO;
                    long o = e ^ aO;
                    long c = (uO & mO) | (e & aO);
                    long p = uT ^ mT;
                    long q = uT & mT;
                    long r = aT ^ c;
                    long t = aT & c;
                    long next0 = ((p ^ r) & ~(q | t)) & (o | m0);
                    long ch0 = (next0 ^ m0) & inner[i];
                    if (ch0 != 0L) {
                        r0[i] = m0 ^ ch0;
                        sectorDelta(s0, i, ch0 & next0, ch0 & m0);
                    }
                }
            }
        }
    }

    private void rowSums(long[] row, long[] odd, long[] twos) {
        final int w = words;
        long prev = row[0];
        long cur = row[1];
        for (int i = 1; i <= w; i++) {
            long nxt = row[i + 1];
            long l = (cur << 1) | (prev >>> 63);
            long r = (cur >>> 1) | (nxt << 63);
            long x = l ^ cur;
            odd[i] = x ^ r;
            twos[i] = (l & cur) | (x & r);
            prev = cur;
            cur = nxt;
        }
    }

    private void sectorDelta(short[] sectorRow, int i, long born, long died) {
        if (seg3) {
            long m = sm0[i];
            int k = sk0[i];
            sectorRow[k] = (short) (sectorRow[k] + Long.bitCount(born & m) - Long.bitCount(died & m));
            m = sm1[i];
            k = sk1[i];
            sectorRow[k] = (short) (sectorRow[k] + Long.bitCount(born & m) - Long.bitCount(died & m));
            m = sm2[i];
            if (m != 0L) {
                k = sk2[i];
                sectorRow[k] = (short) (sectorRow[k] + Long.bitCount(born & m) - Long.bitCount(died & m));
            }
            return;
        }
        final long[] masks = segMask;
        final int[] secs = segSector;
        for (int s = segStart[i], end = segStart[i + 1]; s < end; s++) {
            long m = masks[s];
            int d = Long.bitCount(born & m) - Long.bitCount(died & m);
            int k = secs[s];
            sectorRow[k] = (short) (sectorRow[k] + d);
        }
    }

    private void sectorDelta2(short[] sectorRow, int i, long b0, long d0, long b1, long d1) {
        if (seg3) {
            long m = sm0[i];
            int k = sk0[i];
            sectorRow[k] = (short) (sectorRow[k] + Long.bitCount(b0 & m) + Long.bitCount(b1 & m)
                    - Long.bitCount(d0 & m) - Long.bitCount(d1 & m));
            m = sm1[i];
            k = sk1[i];
            sectorRow[k] = (short) (sectorRow[k] + Long.bitCount(b0 & m) + Long.bitCount(b1 & m)
                    - Long.bitCount(d0 & m) - Long.bitCount(d1 & m));
            m = sm2[i];
            if (m != 0L) {
                k = sk2[i];
                sectorRow[k] = (short) (sectorRow[k] + Long.bitCount(b0 & m) + Long.bitCount(b1 & m)
                        - Long.bitCount(d0 & m) - Long.bitCount(d1 & m));
            }
            return;
        }
        sectorDelta(sectorRow, i, b0, d0);
        sectorDelta(sectorRow, i, b1, d1);
    }

    private void setInitialSectorHeat(Block block) {
        short[][] sector = new short[totalSectors][totalSectors];
        long[][] packed = block.getPackedCells();
        for (int x = 1; x <= blockSize; x++) {
            short[] sectorRow = sector[(x - 1) / sectorSize];
            long[] row = packed[x];
            for (int i = 1; i <= words; i++) {
                long alive = row[i];
                if (alive == 0L) continue;
                for (int s = segStart[i], end = segStart[i + 1]; s < end; s++) {
                    int k = segSector[s];
                    sectorRow[k] = (short) (sectorRow[k] + Long.bitCount(alive & segMask[s]));
                }
            }
        }
        block.setSectorCounts(sector);
    }
}
