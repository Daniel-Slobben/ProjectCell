package slobben.cells.service.worker;

import com.github.luben.zstd.Zstd;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import slobben.cells.entities.Block;
import slobben.cells.service.workers.Packer;
import slobben.cells.util.PackedRows;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static slobben.cells.TestUtils.decodeFull;

/**
 * Times the Packer's FULL encoding on saved blocks of real chaos hits, single threaded, after a warm-up on the same
 * blocks. The blocks are the i.zst files (zstd of the rows) that FullBlockChaosTest writes with -Dchaos.write=dir,
 * in folders per hit age. Every payload is decoded and checked against the rows.
 * <p>
 * mvn test -Dtest=FullBlockEncodeBench -Djunit.jupiter.conditions.deactivate='org.junit.*DisabledCondition' -Dchaos.data=dir,
 * with -Dbench.write=dir to write every payload, to compare two versions byte for byte.
 */
class FullBlockEncodeBench {

    private static final int SIZE = 2000;
    private static final int ROUNDS = Integer.getInteger("bench.rounds", 5);

    // only the cells per saved block: a whole Block is several MB
    record Saved(String group, long[][] rows, byte[] bits) {
    }

    private static final Block BLOCK = new Block(0, 0, UUID.randomUUID(), SIZE);

    @Test
    @Disabled("Benchmark, needs saved blocks")
    void encodeTime() throws IOException {
        List<Saved> saved = load(Path.of(System.getProperty("chaos.data")));
        var threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

        for (int round = 0; round < 3; round++) {
            for (Saved s : saved) encode(s.rows());
        }
        String write = System.getProperty("bench.write");
        for (int i = 0; i < saved.size(); i++) {
            byte[] payload = encode(saved.get(i).rows());
            assertThat(decodeFull(payload, SIZE)).as(saved.get(i).group()).isEqualTo(saved.get(i).bits());
            if (write != null) Files.write(Files.createDirectories(Path.of(write)).resolve(i + ".full"), payload);
        }

        Map<String, List<Long>> nanos = new TreeMap<>(Comparator.comparingInt(FullBlockEncodeBench::age));
        Map<String, List<Long>> zstdNanos = new HashMap<>();
        Map<String, Long> bytes = new HashMap<>(), allocated = new HashMap<>();
        for (int round = 0; round < ROUNDS; round++) {
            for (Saved s : saved) {
                BLOCK.setPackedCells(s.rows());
                Packer.packAll(BLOCK);
                long a0 = threads.getCurrentThreadAllocatedBytes(), t0 = System.nanoTime();
                byte[] payload = BLOCK.getEncodedBlock().encodedCells();
                long t1 = System.nanoTime(), a1 = threads.getCurrentThreadAllocatedBytes();
                nanos.computeIfAbsent(s.group(), g -> new ArrayList<>()).add(t1 - t0);
                // the zstd part alone, on the same bytes
                byte[] raw = Zstd.decompress(payload, (int) Zstd.getFrameContentSize(payload));
                long t2 = System.nanoTime();
                Zstd.compress(raw);
                zstdNanos.computeIfAbsent(s.group(), g -> new ArrayList<>()).add(System.nanoTime() - t2);
                if (round == 0) {
                    bytes.merge(s.group(), (long) payload.length, Long::sum);
                    allocated.merge(s.group(), a1 - a0, Long::sum);
                }
            }
        }

        System.out.printf("%n%-10s %6s %8s %9s %9s %9s %10s %9s%n", "group", "blocks", "KB", "ms avg", "ms p50", "ms p90", "alloc KB", "zstd avg");
        List<Long> all = new ArrayList<>(), allZstd = new ArrayList<>();
        long allBytes = 0, allAllocated = 0;
        for (var e : nanos.entrySet()) {
            int blocks = e.getValue().size() / ROUNDS;
            print(e.getKey(), blocks, bytes.get(e.getKey()), e.getValue(), allocated.get(e.getKey()), zstdNanos.get(e.getKey()));
            all.addAll(e.getValue());
            allZstd.addAll(zstdNanos.get(e.getKey()));
            allBytes += bytes.get(e.getKey());
            allAllocated += allocated.get(e.getKey());
        }
        print("all", saved.size(), allBytes, all, allAllocated, allZstd);
    }

    private static byte[] encode(long[][] rows) {
        BLOCK.setPackedCells(rows);
        Packer.packAll(BLOCK);
        return BLOCK.getEncodedBlock().encodedCells();
    }

    private static void print(String group, int blocks, long bytes, List<Long> nanos, long allocated, List<Long> zstd) {
        long[] sorted = nanos.stream().mapToLong(Long::longValue).sorted().toArray();
        System.out.printf("%-10s %6d %8.1f %9.3f %9.3f %9.3f %10.0f %9.3f%n", group, blocks, bytes / 1024.0 / blocks,
                Arrays.stream(sorted).average().orElse(0) / 1e6, sorted[sorted.length / 2] / 1e6,
                sorted[(int) (sorted.length * 0.9)] / 1e6, allocated / 1024.0 / blocks,
                zstd.stream().mapToLong(Long::longValue).average().orElse(0) / 1e6);
    }

    private static int age(String group) {
        return group.startsWith("age-") ? Integer.parseInt(group.substring(4)) : Integer.MAX_VALUE;
    }

    private static List<Saved> load(Path dir) throws IOException {
        List<Saved> saved = new ArrayList<>();
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".zst")).sorted().toList()) {
                byte[] bits = Zstd.decompress(Files.readAllBytes(file), (SIZE * SIZE + 7) / 8);
                long[][] rows = PackedRows.create(SIZE);
                for (int i = 0; i < SIZE * SIZE; i++) {
                    if ((bits[i >>> 3] >> (i & 7) & 1) != 0) PackedRows.set(rows[i / SIZE + 1], i % SIZE + 1);
                }
                saved.add(new Saved(file.getParent().getFileName().toString(), rows, bits));
            }
        }
        return saved;
    }
}
