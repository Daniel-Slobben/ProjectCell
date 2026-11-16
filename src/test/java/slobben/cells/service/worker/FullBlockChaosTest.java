package slobben.cells.service.worker;

import com.github.luben.zstd.Zstd;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import slobben.cells.entities.Block;
import slobben.cells.entities.ChaosHit;
import slobben.cells.enums.ChaosType;
import slobben.cells.service.RunnerService;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static slobben.cells.TestUtils.decodeFull;

/**
 * FULL blocks of real chaos hits as the Packer sends them, copied rows and runs of strips, against zstd 3 of the rows
 * as it sent them before, as the hits age. ChaosService runs as in production: a random hit every 100 ticks. The
 * blocks of each hit are encoded both ways when the hit is 0, 1, 2, 4 ... 2048 ticks old, and all blocks every 100
 * ticks. Every payload is decoded and checked against the rows. The first 200 ticks only warm up: they encode every
 * block both ways, so the JIT compiles both on real blocks, as in production.
 * <p>
 * mvn test -Dtest=FullBlockChaosTest -Djunit.jupiter.conditions.deactivate='org.junit.*DisabledCondition' -DargLine=-Xmx8g
 * with -Dchaos.ticks=4000 for the length of the run, and -Dchaos.write=dir to write up to 30 blocks per age as
 * dir/age-n/i.zst and i.full for CellWeb's bench/full-block.
 */
@SpringBootTest(properties = {
        "cells.size.blockSize=2000",
        "cells.size.sectorSize=50",
        "cells.pruning-per-generation=100",
        "cells.runmode=MANUAL",
        "cells.targetspeed=0",
        "logging.level.slobben=WARN"
})
class FullBlockChaosTest {

    private static final int SIZE = 2000;
    private static final List<Integer> AGES = List.of(0, 1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048);
    private static final int WORLD_EVERY = 100;
    private static final int WARM_UP = 200;
    private static final int WRITE_PER_AGE = 30;
    private static final VarHandle LONG_LE = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);

    @Autowired
    private RunnerService runnerService;
    @Autowired
    private Map<String, Block> blocks;
    @Autowired
    private List<ChaosHit> chaosHits;

    record Sample(ChaosType type, int age, int tick, int rowsBytes, int stripsBytes, long rowsNanos, long stripsNanos) {
    }

    @Test
    @Disabled("Too expensive for normal test run")
    void stripsAgainstZstdOfTheRows() throws IOException {
        int ticks = Integer.getInteger("chaos.ticks", 2500);
        String write = System.getProperty("chaos.write");

        List<Sample> byAge = new ArrayList<>(), world = new ArrayList<>();
        Map<Integer, Integer> written = new HashMap<>();
        Set<UUID> seen = new HashSet<>();
        int wrong = 0;
        long start = System.nanoTime();
        for (int tick = 1; tick <= ticks; tick++) {
            runnerService.runCycle();
            Map<UUID, ChaosHit> hits = new HashMap<>();
            for (ChaosHit hit : chaosHits) {
                hits.put(hit.getId(), hit);
                if (seen.add(hit.getId())) System.out.printf("tick %d: %s%n", tick, hit.getName());
            }
            for (Block block : blocks.values()) {
                if (tick <= WARM_UP) {
                    block.getEncodedBlock();
                    Zstd.compress(getPacked(block.getPackedCells()));
                    continue;
                }
                ChaosHit hit = hits.get(block.getResponsibleChaosHit());
                boolean atAge = hit != null && AGES.contains(hit.getAge());
                if (!atAge && tick % WORLD_EVERY != 0) continue;

                // the Packer's encoding of this tick, read for the first time here, so this encodes it
                long t0 = System.nanoTime();
                byte[] strips = (tick & 1) == 0 ? block.getEncodedBlock().encodedCells() : null;
                long t1 = System.nanoTime();
                byte[] bits = getPacked(block.getPackedCells());
                byte[] zstd = Zstd.compress(bits);
                long t2 = System.nanoTime();
                if (strips == null) strips = block.getEncodedBlock().encodedCells();
                long t3 = System.nanoTime();
                if (!Arrays.equals(decodeFull(strips, SIZE), bits)) wrong++;

                Sample sample = new Sample(hit == null ? null : hit.getType(), hit == null ? -1 : hit.getAge(), tick,
                        zstd.length, strips.length, t2 - t1, (t1 - t0) + (t3 - t2));
                if (atAge) byAge.add(sample);
                if (tick % WORLD_EVERY == 0) world.add(sample);
                if (atAge && write != null && written.merge(hit.getAge(), 1, Integer::sum) <= WRITE_PER_AGE) {
                    Path dir = Files.createDirectories(Path.of(write, "age-" + hit.getAge()));
                    int n = written.get(hit.getAge());
                    Files.write(dir.resolve(n + ".zst"), zstd);
                    Files.write(dir.resolve(n + ".full"), strips);
                }
            }
            if (tick % 250 == 0) {
                System.out.printf("tick %d: %d blocks, %d s%n", tick, blocks.size(), (System.nanoTime() - start) / 1_000_000_000);
            }
        }

        System.out.printf("%nBy hit age: the blocks of each hit when it is that many ticks old%n");
        print("age", byAge, Sample::age);
        System.out.printf("%nRuns of strips as %% of zstd of the rows, by hit type and age%n");
        printByType(byAge);
        System.out.printf("%nThe whole world every %d ticks%n", WORLD_EVERY);
        print("tick", world, Sample::tick);
        System.out.printf("%nAll%n");
        print("", byAge, s -> "hit ages");
        print("", world, s -> "world");
        assertThat(wrong).as("FULL payloads that decode to other cells").isZero();
    }

    private static void print(String key, List<Sample> samples, Function<Sample, Object> group) {
        System.out.printf("%-10s %5s %6s %8s %8s %7s %16s %16s%n", key, "hits", "blocks", "rows KB", "runs KB", "runs %", "rows ms avg/p90", "runs ms avg/p90");
        Map<Object, List<Sample>> groups = new TreeMap<>(Comparator.comparing(Object::toString, Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder())));
        samples.forEach(s -> groups.computeIfAbsent(group.apply(s), g -> new ArrayList<>()).add(s));
        groups.forEach((name, list) -> {
            long rows = list.stream().mapToLong(Sample::rowsBytes).sum(), strips = list.stream().mapToLong(Sample::stripsBytes).sum();
            long hits = list.stream().map(s -> s.type() + "@" + (s.tick() - s.age())).distinct().count();
            System.out.printf("%-10s %5d %6d %8.1f %8.1f %6.1f%% %16s %16s%n", name, hits, list.size(), rows / 1024.0 / list.size(),
                    strips / 1024.0 / list.size(), 100.0 * strips / rows, millis(list, Sample::rowsNanos), millis(list, Sample::stripsNanos));
        });
    }

    private static void printByType(List<Sample> samples) {
        System.out.printf("%-15s", "");
        AGES.forEach(age -> System.out.printf("%6d", age));
        System.out.println();
        for (ChaosType type : ChaosType.values()) {
            System.out.printf("%-15s", type);
            for (int age : AGES) {
                List<Sample> list = samples.stream().filter(s -> s.type() == type && s.age() == age).toList();
                long rows = list.stream().mapToLong(Sample::rowsBytes).sum(), strips = list.stream().mapToLong(Sample::stripsBytes).sum();
                System.out.printf("%6s", list.isEmpty() ? "" : Math.round(100.0 * strips / rows));
            }
            System.out.println();
        }
    }

    private static String millis(List<Sample> list, Function<Sample, Long> nanos) {
        long[] sorted = list.stream().mapToLong(nanos::apply).sorted().toArray();
        return "%.2f / %.2f".formatted(Arrays.stream(sorted).average().orElse(0) / 1e6, sorted[(int) (sorted.length * 0.9)] / 1e6);
    }

    // the Packer's FULL bytes before strips: the rows one after the other, cell x * size + y
    private static byte[] getPacked(long[][] packedCells) {
        final int wholeWords = SIZE >>> 6;
        final long tailMask = (1L << (SIZE & 63)) - 1;
        BitStream out = new BitStream();
        for (int x = 1; x <= SIZE; x++) {
            long[] row = packedCells[x];
            for (int i = 1; i <= wholeWords; i++) {
                out.write64(row[i] >>> 1 | row[i + 1] << 63);
            }
            out.writeTail((row[wholeWords + 1] >>> 1 | row[wholeWords + 2] << 63) & tailMask);
        }
        return out.finish();
    }

    private static final class BitStream {
        private final byte[] packed = new byte[(SIZE * SIZE + 7) / 8];
        private final int tailCells = SIZE & 63;
        private long pending = 0;
        private int pendingBits = 0;
        private int pos = 0;

        void write64(long bits) {
            LONG_LE.set(packed, pos, pending | bits << pendingBits);
            pos += 8;
            pending = (bits >>> 1) >>> (63 - pendingBits);
        }

        void writeTail(long bits) {
            if (tailCells == 0) return;
            pending |= bits << pendingBits;
            pendingBits += tailCells;
            if (pendingBits >= 64) {
                LONG_LE.set(packed, pos, pending);
                pos += 8;
                pendingBits -= 64;
                pending = bits >>> (tailCells - pendingBits);
            }
        }

        byte[] finish() {
            for (; pos < packed.length; pos++, pending >>>= 8) {
                packed[pos] = (byte) pending;
            }
            return packed;
        }
    }
}
