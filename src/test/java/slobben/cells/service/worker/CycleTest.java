package slobben.cells.service.worker;

import com.github.luben.zstd.Zstd;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;
import slobben.cells.TestUtils.Cell;
import slobben.cells.dto.outgoing.EncodedBlock;
import slobben.cells.dto.outgoing.EncodedBlockType;
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
import static slobben.cells.TestUtils.*;

// the cycle of RunnerService on random cells, against a plain Game of Life on an unbounded grid
class CycleTest {
    private static final int GENERATIONS = 120;
    private static final int HIT_GENERATION = 60;
    private static final String PULSAR = """
            ..###...###..
            .............
            #....#.#....#
            #....#.#....#
            #....#.#....#
            ..###...###..
            .............
            ..###...###..
            #....#.#....#
            #....#.#....#
            #....#.#....#
            .............
            ..###...###..""";

    private final Map<String, Block> blocks = new HashMap<>();
    private final Map<String, Block> newBlocks = new ConcurrentHashMap<>();
    private final ExecutorService executorService = new ExecutorService();
    private final AlgorithmGol algorithmGol = new AlgorithmGol(executorService, blocks);
    private final Stitching stitching = new Stitching(executorService, blocks, newBlocks);
    private final AddNewBlocks addNewBlocks = new AddNewBlocks(newBlocks, blocks);
    private final Packer packer = new Packer(blocks);
    private final LoopService loopService = new LoopService(blocks);
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
        Set<Cell> expected = addRandomCells(random, -size / 2, -size / 2, 2 * size, 2 * size);
        List<Encoding> encodings = List.of();

        for (int generation = 1; generation <= GENERATIONS; generation++) {
            // halfway a chaos hit replaces the blocks it lands on
            Set<Cell> hit = generation == HIT_GENERATION ? addRandomCells(random, 0, size / 4, size * 3 / 2, size / 4) : Set.of();

            // execute
            runCycle(encodings);

            // verify
            // the random cells come in with the first tick, the GoL steps them from the second
            if (generation > 1) expected = nextGeneration(expected);
            if (!hit.isEmpty()) expected = applyChaosHit(expected, hit);
            assertThat(findDifferences(readActualCells(), expected)).as("generation %d", generation).isEmpty();
            assertThat(PackedRows.emptyRow(size)).as("generation %d empty row", generation).containsOnly(0L);
            encodings = getExpectedEncodings(generation);
        }
    }

    @ParameterizedTest
    @CsvSource({"64, 8", "70, 10", "75, 15"})
    void hibernatingBlocksFollowTheGameOfLife(int size, int sectorSize) {
        // prepare
        setSizes(size, sectorSize);
        Set<Cell> expected = new HashSet<>();
        expected.addAll(addPattern(2, 2, "##\n##"));
        expected.addAll(addPattern(10, 3, "###"));
        expected.addAll(addPattern(20, 20, PULSAR));
        expected.addAll(addPattern(size + 16, size + 16, "###\n#..\n.#."));
        List<Encoding> encodings = List.of();
        boolean hibernated = false;

        for (int generation = 1; generation <= 2 * GENERATIONS; generation++) {
            // execute
            runCycle(encodings);

            // verify
            if (generation > 1) expected = nextGeneration(expected);
            assertThat(findDifferences(readActualCells(), expected)).as("generation %d", generation).isEmpty();
            encodings = getExpectedEncodings(generation);
            hibernated |= blocks.get(BlockUtils.getKey(0, 0)).getBlockState() == BlockState.HIBERNATION;
        }
        assertThat(hibernated).isTrue();
        // the glider woke it
        assertThat(blocks.get(BlockUtils.getKey(0, 0)).getBlockState()).isEqualTo(BlockState.ACTIVE);
    }

    private void runCycle(List<Encoding> encodings) {
        algorithmGol.execute();
        addNewBlocks.execute();
        stitching.execute();
        addNewBlocks.execute();
        // the encodings of the tick before are read after the GoL and Stitching of this tick
        encodings.forEach(this::checkEncoding);
        packer.execute();
        loopService.execute();
        pruning.execute();
    }

    private Set<Cell> addPattern(int x, int y, String pattern) {
        Set<Cell> cells = new HashSet<>();
        List<String> lines = pattern.lines().toList();
        for (int i = 0; i < lines.size(); i++) {
            for (int j = 0; j < lines.get(i).length(); j++) {
                if (lines.get(i).charAt(j) != '#') continue;
                worldEditor.setCell(x + i, y + j, null);
                cells.add(new Cell(x + i, y + j));
            }
        }
        return cells;
    }

    private void setSizes(int size, int sectorSize) {
        this.size = size;
        ReflectionTestUtils.setField(algorithmGol, "blockSize", size);
        ReflectionTestUtils.setField(algorithmGol, "sectorSize", sectorSize);
        ReflectionTestUtils.invokeMethod(algorithmGol, "init");
        ReflectionTestUtils.setField(stitching, "blockSize", size);
        ReflectionTestUtils.invokeMethod(stitching, "init");
        ReflectionTestUtils.setField(pruning, "pruningPerGeneration", 25);
        ReflectionTestUtils.setField(loopService, "blockSize", size);
        ReflectionTestUtils.setField(loopService, "checkEvery", 6);
        ReflectionTestUtils.invokeMethod(loopService, "init");
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
            long[][] packed = getCells(block, size);
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
            long[][] packed = getCells(block, size);
            byte[] full = getExpectedFullBits(packed);
            encodings.add(new Encoding(what + " full", block::getEncodedBlock, block.getGeneration(), full));
            encodings.add(new Encoding(what + " borders", block::getEncodedBlockBorders, block.getGeneration(),
                    block.getBlockState() == BlockState.NEW ? full : getExpectedBorderBits(packed)));
        }
        return encodings;
    }

    private void checkEncoding(Encoding expected) {
        EncodedBlock encoded = expected.encoding().get();
        assertThat(encoded.generation()).as(expected.what()).isEqualTo(expected.generation());
        byte[] bits = encoded.type() == EncodedBlockType.FULL
                ? decodeFull(encoded.encodedCells(), size)
                : Zstd.decompress(encoded.encodedCells(), expected.bits().length);
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

}
