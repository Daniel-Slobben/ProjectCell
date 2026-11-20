package slobben.cells;

import com.github.luben.zstd.Zstd;
import org.springframework.test.util.ReflectionTestUtils;
import slobben.cells.entities.Block;
import slobben.cells.entities.Loop;
import slobben.cells.enums.BlockState;
import slobben.cells.service.ExecutorService;
import slobben.cells.service.workers.AlgorithmGol;
import slobben.cells.service.workers.Packer;
import slobben.cells.util.PackedRows;

import java.util.*;

public final class TestUtils {

    public record Cell(int x, int y) {
    }

    private TestUtils() {
    }

    /**
     * Reference game of life imp for testing
     *
     * @param alive set of live Cells
     * @return returns next state
     */
    public static Set<Cell> nextGeneration(Set<Cell> alive) {
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

    public static AlgorithmGol createAlgorithmGol(int blockSize, int sectorSize) {
        AlgorithmGol algorithmGol = new AlgorithmGol(new ExecutorService(), new HashMap<>());
        ReflectionTestUtils.setField(algorithmGol, "blockSize", blockSize);
        ReflectionTestUtils.setField(algorithmGol, "sectorSize", sectorSize);
        ReflectionTestUtils.invokeMethod(algorithmGol, "init");
        return algorithmGol;
    }

    // Stitching packs NEW blocks and writes the borders before the GoL runs, here the drawn borders come along
    public static void nextState(AlgorithmGol algorithmGol, Block block) {
        if (block.getBlockState() == BlockState.NEW) {
            block.setPackedCells(PackedRows.pack(block.getCells()));
        }
        algorithmGol.setNextState(block);
    }

    public static long[][] getCells(Block block, int size) {
        Loop loop = block.getLoop();
        return loop == null ? block.getPackedCells() : Packer.readFull(loop.phase().full(), size);
    }

    public static void drawPattern(Block block, int x, int y, String pattern) {
        List<String> lines = pattern.lines().toList();
        for (int i = 0; i < lines.size(); i++) {
            for (int j = 0; j < lines.get(i).length(); j++) {
                block.getCells()[x + i][y + j] = lines.get(i).charAt(j) == '#';
            }
        }
    }

    // a FULL block as the frontend reads it: the rows that copy an earlier row up to a zero, then per run a varint of
    // the empty bytes before it and its bytes down a strip
    public static byte[] decodeFull(byte[] payload, int size) {
        byte[] runs = Zstd.decompress(payload, (int) Zstd.getFrameContentSize(payload));
        int columns = (size + 7) / 8;
        byte[] bits = new byte[(size * size + 7) / 8];
        int[] i = {0};
        List<int[]> copies = new ArrayList<>();
        for (int x = 0, distance = 0; runs[i[0]] != 0; ) {
            x += readVarint(runs, i);
            int further = readVarint(runs, i);
            distance += further >>> 1 ^ -(further & 1);
            copies.add(new int[]{x, x - distance});
        }
        i[0]++;
        for (int position = 0; i[0] < runs.length; i[0]++) {
            position += readVarint(runs, i);
            int strip = position / 64, c = strip % columns;
            for (int x = strip / columns * 64 + position % 64; runs[i[0]] != 0; i[0]++, x++, position++) {
                for (int k = 0; k < 8; k++) {
                    if ((runs[i[0]] >> k & 1) != 0) setBit(bits, x * size + 8 * c + k);
                }
            }
        }
        for (int[] copy : copies) {
            for (int y = 0; y < size; y++) {
                int from = copy[1] * size + y;
                if ((bits[from >>> 3] >> (from & 7) & 1) != 0) setBit(bits, copy[0] * size + y);
            }
        }
        return bits;
    }

    public static void setBit(byte[] bits, int i) {
        bits[i >>> 3] |= (byte) (1 << (i & 7));
    }

    private static int readVarint(byte[] bytes, int[] i) {
        int value = 0;
        for (int shift = 0; ; shift += 7) {
            value += (bytes[i[0]] & 0x7F) << shift;
            if (bytes[i[0]++] >= 0) return value;
        }
    }
}
