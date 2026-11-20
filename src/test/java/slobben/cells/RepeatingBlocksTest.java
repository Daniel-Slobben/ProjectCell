package slobben.cells;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import slobben.cells.entities.Block;
import slobben.cells.service.RunnerService;
import slobben.cells.service.ServerInfoService;
import slobben.cells.util.PackedRows;

import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Runs the world with real chaos hits and reports the heap and the blocks and tiles that repeat. Run with: mvn test
 * -Dtest=RepeatingBlocksTest -Djunit.jupiter.conditions.deactivate='org.junit.*DisabledCondition'
 * -DargLine="-Xmx4g -XX:ActiveProcessorCount=4" -Drepeating.minutes=45
 */
@SpringBootTest(properties = {
        "cells.size.blockSize=2000",
        "cells.size.sectorSize=50",
        "cells.pruning-per-generation=100",
        "cells.runmode=MANUAL",
        "cells.targetspeed=0",
        "cells.chaos.type=ANY",
        "cells.chaos.max-blocks=1000",
        "logging.level.slobben=WARN",
        "logging.level.slobben.cells.service.workers.ChaosService=INFO"
})
class RepeatingBlocksTest {

    private static final long RUN_MILLIS = Long.getLong("repeating.minutes", 15) * 60_000;
    private static final int WINDOW_EVERY = 500;
    private static final int WINDOW = 32;
    private static final int MAX_PERIOD = WINDOW / 2;
    private static final int LONG_STREAK = 5;
    private static final int TILE = 64;

    @Autowired
    private RunnerService runnerService;
    @Autowired
    private ServerInfoService serverInfoService;
    @Autowired
    private Map<String, Block> blocks;
    @Value("${cells.size.blockSize}")
    private int blockSize;

    private long[] innerMask;
    private final Map<String, long[]> hashes = new HashMap<>();
    private final Map<String, Tiles> tiles = new HashMap<>();
    private Map<String, Set<Long>> lastPhases = new HashMap<>();
    private Map<String, Integer> streaks = new HashMap<>();

    private static final class Tiles {
        final boolean[] empty;
        final boolean[] repeating;
        long[] last;
        long[] beforeLast;

        Tiles(long[] hashes) {
            empty = new boolean[hashes.length];
            repeating = new boolean[hashes.length];
            for (int i = 0; i < hashes.length; i++) {
                empty[i] = hashes[i] == 0;
                repeating[i] = true;
            }
            last = hashes;
        }

        void add(long[] hashes) {
            for (int i = 0; i < hashes.length; i++) {
                empty[i] &= hashes[i] == 0;
                if (beforeLast != null) repeating[i] &= hashes[i] == beforeLast[i];
            }
            beforeLast = last;
            last = hashes;
        }
    }

    @Test
    @Disabled("Runs for minutes")
    @SuppressWarnings("java:S2699")
    void repeatingBlocks() {
        innerMask = PackedRows.innerMask(blockSize);
        long start = System.currentTimeMillis();
        long tickMillis = 0;
        for (int tick = 1; System.currentTimeMillis() - start < RUN_MILLIS; tick++) {
            long tickStart = System.currentTimeMillis();
            runnerService.runCycle();
            tickMillis += System.currentTimeMillis() - tickStart;
            int index = tick % WINDOW_EVERY - (WINDOW_EVERY - WINDOW);
            if (index >= 0) {
                record(index);
                if (index == WINDOW - 1) {
                    report(tick, (System.currentTimeMillis() - start) / 60_000.0, (double) tickMillis / WINDOW_EVERY);
                    tickMillis = 0;
                }
            }
        }
        serverInfoService.getAverageWorkTimes().forEach((worker, ms) -> System.out.printf("WORKER %-30s %.3fms%n", worker, ms));
    }

    private void record(int index) {
        Map<String, long[]> now = blocks.values().parallelStream()
                .collect(Collectors.toConcurrentMap(Block::getKey, block -> tileHashes(block.getPackedCells())));
        if (index == 0) {
            hashes.clear();
            tiles.clear();
            now.forEach((key, tileHashes) -> {
                hashes.put(key, new long[WINDOW]);
                tiles.put(key, new Tiles(tileHashes));
            });
        } else {
            tiles.keySet().retainAll(now.keySet());
            tiles.forEach((key, blockTiles) -> blockTiles.add(now.get(key)));
        }
        hashes.keySet().retainAll(now.keySet());
        hashes.forEach((key, history) -> history[index] = hash(now.get(key)));
    }

    private void report(int tick, double minutes, double averageTick) {
        int empty = 0, notRepeating = 0, stayed = 0, longStayed = 0;
        Map<Integer, Integer> periods = new TreeMap<>();
        Map<Block, Integer> repeating = new HashMap<>();
        Map<String, Set<Long>> phases = new HashMap<>();
        Map<String, Integer> newStreaks = new HashMap<>();
        for (var entry : hashes.entrySet()) {
            long[] history = entry.getValue();
            int period = shortestPeriod(history);
            if (period == 0) {
                notRepeating++;
            } else if (period == 1 && history[0] == 0) {
                empty++;
            } else {
                periods.merge(period, 1, Integer::sum);
                repeating.put(blocks.get(entry.getKey()), period);
                Set<Long> blockPhases = new HashSet<>();
                for (int i = 0; i < period; i++) blockPhases.add(history[i]);
                phases.put(entry.getKey(), blockPhases);
                int streak = blockPhases.equals(lastPhases.get(entry.getKey())) ? streaks.get(entry.getKey()) + 1 : 1;
                newStreaks.put(entry.getKey(), streak);
                if (streak > 1) stayed++;
                if (streak >= LONG_STREAK) longStayed++;
            }
        }
        lastPhases = phases;
        streaks = newStreaks;

        long tileCount = 0, emptyTiles = 0, repeatingTiles = 0;
        for (Tiles blockTiles : tiles.values()) {
            for (int i = 0; i < blockTiles.empty.length; i++) {
                tileCount++;
                if (blockTiles.empty[i]) emptyTiles++;
                else if (blockTiles.repeating[i]) repeatingTiles++;
            }
        }

        long frozenBytes = repeating.entrySet().parallelStream()
                .mapToLong(entry -> (long) entry.getKey().getEncodedBlock().encodedCells().length * entry.getValue())
                .sum();
        long liveCells = blocks.values().parallelStream().mapToLong(block -> liveCells(block.getPackedCells())).sum();
        long rowsWithCells = blocks.values().parallelStream().mapToLong(block -> rowsWithCells(block.getPackedCells())).sum();
        System.gc();
        long heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
        double heapPerBlock = (double) heap / blocks.size();

        System.out.printf("tick %d (%.1f min, %.0f ms per tick): %d blocks, %d live cells, heap %d MB after GC (%.2f MB per block), rows with cells %.1f%%%n",
                tick, minutes, averageTick, blocks.size(), liveCells, heap >> 20, heapPerBlock / (1 << 20),
                100.0 * rowsWithCells / ((long) blocks.size() * blockSize));
        System.out.printf("  %d blocks through the window: empty %d, repeating %d %s, not repeating %d%n",
                hashes.size(), empty, repeating.size(), periods, notRepeating);
        System.out.printf("  repeating: same as %d ticks ago %d, same for %d+ ticks %d; FULL x period %d MB (%.0f KB per block), frees ~%d MB%n",
                WINDOW_EVERY, stayed, (LONG_STREAK - 1) * WINDOW_EVERY, longStayed, frozenBytes >> 20,
                repeating.isEmpty() ? 0 : frozenBytes / 1024.0 / repeating.size(),
                (long) (repeating.size() * heapPerBlock - frozenBytes) >> 20);
        System.out.printf("  tiles of %dx%d: empty %.1f%%, repeating with period 1 or 2 %.1f%%, changing %.1f%%%n",
                TILE, TILE, 100.0 * emptyTiles / tileCount, 100.0 * repeatingTiles / tileCount,
                100.0 * (tileCount - emptyTiles - repeatingTiles) / tileCount);
    }

    private static int shortestPeriod(long[] history) {
        for (int period = 1; period <= MAX_PERIOD; period++) {
            int t = 0;
            while (t + period < WINDOW && history[t] == history[t + period]) t++;
            if (t + period == WINDOW) return period;
        }
        return 0;
    }

    private long[] tileHashes(long[][] cells) {
        int words = cells[0].length - 2;
        long[] tileHashes = new long[(blockSize + TILE - 1) / TILE * words];
        for (int x = 1; x <= blockSize; x++) {
            int first = (x - 1) / TILE * words;
            for (int i = 1; i <= words; i++) {
                tileHashes[first + i - 1] = (tileHashes[first + i - 1] + (cells[x][i] & innerMask[i])) * 0x9E3779B97F4A7C15L;
            }
        }
        return tileHashes;
    }

    private static long hash(long[] tileHashes) {
        long hash = 0;
        for (long tileHash : tileHashes) hash = (hash + tileHash) * 0x9E3779B97F4A7C15L;
        return hash;
    }

    private long liveCells(long[][] cells) {
        long live = 0;
        for (int x = 1; x <= blockSize; x++) {
            for (int i = 1; i < cells[x].length - 1; i++) live += Long.bitCount(cells[x][i] & innerMask[i]);
        }
        return live;
    }

    private long rowsWithCells(long[][] cells) {
        long rows = 0;
        for (int x = 1; x <= blockSize; x++) {
            long any = 0;
            for (int i = 1; i < cells[x].length - 1; i++) any |= cells[x][i] & innerMask[i];
            if (any != 0) rows++;
        }
        return rows;
    }
}
