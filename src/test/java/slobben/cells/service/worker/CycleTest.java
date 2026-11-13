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
import slobben.cells.util.BlockUtils;
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

    record Cell(int x, int y) {
    }

    record Encoding(String what, Supplier<EncodedBlock> encoding, int generation, byte[] bits) {
    }

    @ParameterizedTest
    @CsvSource({"70, 10", "64, 8", "130, 26", "75, 15"})
    void cycleFollowsTheGameOfLife(int size, int sectorSize) {
        // prepare
        setSizes(size, sectorSize);
        Random random = new Random(size * 31L + sectorSize);
        Set<Cell> expected = addRandomCells(random, -size / 2, -size / 2, 2 * size, 2 * size);
        List<Encoding> encodings = List.of();

        for (int generation = 1; generation <= GENERATIONS; generation++) {
            // halfway a chaos hit replaces the blocks it lands on
            Set<Cell> hit = generation == HIT_GENERATION ? addRandomCells(random, 0, size / 4, size * 3 / 2, size / 4) : Set.of();

            // execute
            algorithmGol.execute();
            addNewBlocks.execute();
            stitching.execute();
            addNewBlocks.execute();
            // the encodings of the tick before are read after the GoL and Stitching of this tick
            encodings.forEach(CycleTest::checkEncoding);
            packer.execute();
            pruning.execute();

            // verify
            // the random cells come in with the first tick, the GoL steps them from the second
            if (generation > 1) expected = referenceNextGeneration(expected);
            if (!hit.isEmpty()) expected = applyChaosHit(expected, hit);
            assertThat(findDifferences(readActualCells(), expected)).as("generation %d", generation).isEmpty();
            encodings = getExpectedEncodings(generation);
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

    // about a third of the cells in the area become alive, in the world and in the returned set
    private Set<Cell> addRandomCells(Random random, int x, int y, int rows, int columns) {
        Set<Cell> cells = new HashSet<>();
        for (int i = x; i < x + rows; i++) {
            for (int j = y; j < y + columns; j++) {
                if (random.nextInt(3) == 0) {
                    worldEditor.setCell(i, j, null);
                    cells.add(new Cell(i, j));
                }
            }
        }
        return cells;
    }

    // the reference: a plain Game of Life on the set of alive cells
    private static Set<Cell> referenceNextGeneration(Set<Cell> alive) {
        Map<Cell, Integer> neighbours = new HashMap<>();
        for (Cell cell : alive) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx != 0 || dy != 0) neighbours.merge(new Cell(cell.x() + dx, cell.y() + dy), 1, Integer::sum);
                }
            }
        }
        Set<Cell> next = new HashSet<>();
        neighbours.forEach((cell, count) -> {
            if (count == 3 || (count == 2 && alive.contains(cell))) next.add(cell);
        });
        return next;
    }

    // the hit blocks hold only the cells of the hit
    private Set<Cell> applyChaosHit(Set<Cell> alive, Set<Cell> hit) {
        Set<String> hitBlocks = new HashSet<>();
        hit.forEach(cell -> hitBlocks.add(getBlockKey(cell)));
        Set<Cell> next = new HashSet<>(hit);
        alive.stream().filter(cell -> !hitBlocks.contains(getBlockKey(cell))).forEach(next::add);
        return next;
    }

    private String getBlockKey(Cell cell) {
        return BlockUtils.getKey(Math.floorDiv(cell.x(), size), Math.floorDiv(cell.y(), size));
    }

    private Set<Cell> readActualCells() {
        Set<Cell> cells = new HashSet<>();
        for (Block block : blocks.values()) {
            long[][] packed = block.getPackedCells();
            for (int x = 1; x <= size; x++) {
                for (int y = 1; y <= size; y++) {
                    if (PackedRows.get(packed[x], y)) cells.add(new Cell(block.getX() * size + x - 1, block.getY() * size + y - 1));
                }
            }
        }
        return cells;
    }

    private static List<String> findDifferences(Set<Cell> actual, Set<Cell> expected) {
        List<String> differences = new ArrayList<>();
        actual.stream().filter(cell -> !expected.contains(cell)).limit(10).forEach(cell -> differences.add("extra " + cell));
        expected.stream().filter(cell -> !actual.contains(cell)).limit(10).forEach(cell -> differences.add("missing " + cell));
        return differences;
    }

    // the cells of every block as the Packer encodes them: all inner cells, and only the borders of a block that is not new
    private List<Encoding> getExpectedEncodings(int generation) {
        List<Encoding> encodings = new ArrayList<>();
        for (Block block : blocks.values()) {
            String what = "generation %d block %s".formatted(generation, block.getKey());
            long[][] packed = block.getPackedCells();
            byte[] full = getExpectedFullBits(packed);
            encodings.add(new Encoding(what + " full", block::getEncodedBlock, block.getGeneration(), full));
            encodings.add(new Encoding(what + " borders", block::getEncodedBlockBorders, block.getGeneration(),
                    block.getBlockState() == BlockState.NEW ? full : getExpectedBorderBits(packed)));
        }
        return encodings;
    }

    private static void checkEncoding(Encoding expected) {
        EncodedBlock encoded = expected.encoding().get();
        assertThat(encoded.generation()).as(expected.what()).isEqualTo(expected.generation());
        byte[] bits = LZ4Factory.fastestInstance().safeDecompressor().decompress(encoded.encodedCells(), expected.bits().length);
        assertThat(bits).as(expected.what()).isEqualTo(expected.bits());
    }

    private byte[] getExpectedFullBits(long[][] packed) {
        byte[] bits = new byte[(size * size + 7) / 8];
        for (int x = 1; x <= size; x++) {
            for (int y = 1; y <= size; y++) {
                if (PackedRows.get(packed[x], y)) setBit(bits, (x - 1) * size + y - 1);
            }
        }
        return bits;
    }

    private byte[] getExpectedBorderBits(long[][] packed) {
        byte[] bits = new byte[(size * 4 - 4 + 7) / 8];
        int i = 0;
        for (int x = 1; x <= size; x++, i++) if (PackedRows.get(packed[x], size)) setBit(bits, i);
        for (int x = 1; x <= size; x++, i++) if (PackedRows.get(packed[x], 1)) setBit(bits, i);
        for (int y = 2; y < size; y++, i++) if (PackedRows.get(packed[1], y)) setBit(bits, i);
        for (int y = 2; y < size; y++, i++) if (PackedRows.get(packed[size], y)) setBit(bits, i);
        return bits;
    }

    private static void setBit(byte[] bits, int i) {
        bits[i >>> 3] |= (byte) (1 << (i & 7));
    }
}
