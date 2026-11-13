package slobben.cells.service.worker;

import net.jpountz.lz4.LZ4Factory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;
import slobben.cells.dto.outgoing.EncodedBlock;
import slobben.cells.entities.Block;
import slobben.cells.enums.BlockState;
import slobben.cells.service.ExecutorService;
import slobben.cells.service.WorldEditor;
import slobben.cells.service.workers.*;
import slobben.cells.util.PackedRows;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

// the cycle of RunnerService on random cells, against a plain Game of Life on an unbounded grid
class CycleTest {
    private static final int GENERATIONS = 120;
    private static final int HIT_GENERATION = 60;

    private final Map<String, Block> blocks = new HashMap<>();
    private final Map<String, Block> newBlocks = new ConcurrentHashMap<>();
    private final ExecutorService executorService = new ExecutorService();
    private final AlgorithmGol algorithmGol = new AlgorithmGol(executorService, blocks);
    private final Stitching stitching = new Stitching(executorService, blocks, newBlocks);
    private final AddNewBlocks addNewBlocks = new AddNewBlocks(newBlocks, blocks);
    private final Packer packer = new Packer(blocks);
    private final Pruning pruning = new Pruning(blocks);
    private final WorldEditor worldEditor = new WorldEditor(newBlocks);
    private int size;

    record Encoding(String what, Supplier<EncodedBlock> encoding, int generation, byte[] bits) {
    }

    @ParameterizedTest
    @CsvSource({"70, 10", "64, 8", "130, 26", "75, 15"})
    void cycleFollowsTheGameOfLife(int size, int sectorSize) {
        // prepare
        setSizes(size, sectorSize);
        Random random = new Random(size * 31L + sectorSize);
        Set<Long> expected = soup(random, -size / 2, -size / 2, 2 * size, 2 * size);
        List<Encoding> encodings = List.of();

        for (int generation = 1; generation <= GENERATIONS; generation++) {
            // halfway a chaos hit replaces the blocks it lands on
            Set<Long> hit = generation == HIT_GENERATION ? soup(random, 0, size / 4, size * 3 / 2, size / 4) : Set.of();

            // execute
            algorithmGol.execute();
            addNewBlocks.execute();
            stitching.execute();
            addNewBlocks.execute();
            // the encodings of the tick before are read after the GoL and Stitching of this tick
            encodings.forEach(CycleTest::check);
            packer.execute();
            pruning.execute();

            // verify
            // the soup comes in with the first tick, the GoL steps it from the second
            if (generation > 1) expected = step(expected);
            if (!hit.isEmpty()) expected = replaceBlocks(expected, hit);
            assertThat(differences(cells(), expected)).as("generation %d", generation).isEmpty();
            encodings = encodings(generation);
        }
    }

    private void setSizes(int size, int sectorSize) {
        this.size = size;
        ReflectionTestUtils.setField(algorithmGol, "blockSize", size);
        ReflectionTestUtils.setField(algorithmGol, "sectorSize", sectorSize);
        ReflectionTestUtils.invokeMethod(algorithmGol, "init");
        ReflectionTestUtils.setField(stitching, "blockSize", size);
        ReflectionTestUtils.invokeMethod(stitching, "init");
        ReflectionTestUtils.setField(pruning, "pruningPerGeneration", 25);
        ReflectionTestUtils.setField(worldEditor, "blockSize", size);
    }

    // the cells of every block as the Packer encodes them: all inner cells, and only the borders of a block that is not new
    private List<Encoding> encodings(int generation) {
        List<Encoding> encodings = new ArrayList<>();
        for (Block block : blocks.values()) {
            String what = "generation %d block %s".formatted(generation, block.getKey());
            long[][] packed = block.getPackedCells();
            byte[] full = fullBits(packed);
            encodings.add(new Encoding(what + " full", block::getEncodedBlock, block.getGeneration(), full));
            encodings.add(new Encoding(what + " borders", block::getEncodedBlockBorders, block.getGeneration(),
                    block.getBlockState() == BlockState.NEW ? full : borderBits(packed)));
        }
        return encodings;
    }

    private static void check(Encoding expected) {
        EncodedBlock encoded = expected.encoding().get();
        assertThat(encoded.generation()).as(expected.what()).isEqualTo(expected.generation());
        byte[] bits = LZ4Factory.fastestInstance().safeDecompressor().decompress(encoded.encodedCells(), expected.bits().length);
        assertThat(bits).as(expected.what()).isEqualTo(expected.bits());
    }

    private Set<Long> soup(Random random, int x, int y, int height, int width) {
        Set<Long> cells = new HashSet<>();
        for (int i = x; i < x + height; i++) {
            for (int j = y; j < y + width; j++) {
                if (random.nextInt(3) == 0) {
                    worldEditor.setCell(i, j, null);
                    cells.add(key(i, j));
                }
            }
        }
        return cells;
    }

    private static Set<Long> step(Set<Long> cells) {
        Map<Long, Integer> neighbours = new HashMap<>();
        for (long cell : cells) {
            int x = (int) (cell >> 32), y = (int) cell;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx != 0 || dy != 0) neighbours.merge(key(x + dx, y + dy), 1, Integer::sum);
                }
            }
        }
        Set<Long> next = new HashSet<>();
        neighbours.forEach((cell, count) -> {
            if (count == 3 || (count == 2 && cells.contains(cell))) next.add(cell);
        });
        return next;
    }

    // the hit blocks hold only the cells of the hit
    private Set<Long> replaceBlocks(Set<Long> cells, Set<Long> hit) {
        Set<Long> hitBlocks = new HashSet<>();
        hit.forEach(cell -> hitBlocks.add(blockOf(cell)));
        Set<Long> replaced = new HashSet<>(hit);
        cells.stream().filter(cell -> !hitBlocks.contains(blockOf(cell))).forEach(replaced::add);
        return replaced;
    }

    private long blockOf(long cell) {
        return key(Math.floorDiv((int) (cell >> 32), size), Math.floorDiv((int) cell, size));
    }

    private Set<Long> cells() {
        Set<Long> cells = new HashSet<>();
        for (Block block : blocks.values()) {
            long[][] packed = block.getPackedCells();
            for (int x = 1; x <= size; x++) {
                for (int y = 1; y <= size; y++) {
                    if (PackedRows.get(packed[x], y)) cells.add(key(block.getX() * size + x - 1, block.getY() * size + y - 1));
                }
            }
        }
        return cells;
    }

    private static List<String> differences(Set<Long> actual, Set<Long> expected) {
        List<String> differences = new ArrayList<>();
        actual.stream().filter(cell -> !expected.contains(cell)).limit(10).forEach(cell -> differences.add("extra " + name(cell)));
        expected.stream().filter(cell -> !actual.contains(cell)).limit(10).forEach(cell -> differences.add("missing " + name(cell)));
        return differences;
    }

    private byte[] fullBits(long[][] packed) {
        byte[] bits = new byte[(size * size + 7) / 8];
        for (int x = 1; x <= size; x++) {
            for (int y = 1; y <= size; y++) {
                if (PackedRows.get(packed[x], y)) set(bits, (x - 1) * size + y - 1);
            }
        }
        return bits;
    }

    private byte[] borderBits(long[][] packed) {
        byte[] bits = new byte[(size * 4 - 4 + 7) / 8];
        int i = 0;
        for (int x = 1; x <= size; x++, i++) if (PackedRows.get(packed[x], size)) set(bits, i);
        for (int x = 1; x <= size; x++, i++) if (PackedRows.get(packed[x], 1)) set(bits, i);
        for (int y = 2; y < size; y++, i++) if (PackedRows.get(packed[1], y)) set(bits, i);
        for (int y = 2; y < size; y++, i++) if (PackedRows.get(packed[size], y)) set(bits, i);
        return bits;
    }

    private static void set(byte[] bits, int i) {
        bits[i >>> 3] |= (byte) (1 << (i & 7));
    }

    private static long key(int x, int y) {
        return (long) x << 32 | (y & 0xffffffffL);
    }

    private static String name(long cell) {
        return "(%d, %d)".formatted((int) (cell >> 32), (int) cell);
    }
}
