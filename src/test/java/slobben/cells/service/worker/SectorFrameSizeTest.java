package slobben.cells.service.worker;

import com.github.luben.zstd.Zstd;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import slobben.cells.dto.outgoing.EncodedBlock;
import slobben.cells.entities.Block;
import slobben.cells.service.RunnerService;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The size of a SECTOR frame per sector size, on real chaos hits: ChaosService runs as in production, a random hit
 * every 100 ticks. Every 100 ticks each block's alive cells are counted per sector of every size, from the same cells,
 * and packed like Packer does: u16 little-endian, zstd 3. BORDER frames are the comparison: a client in cells mode
 * gets one per block per tick, a client in sectors mode one SECTOR per block every 4 ticks.
 * <p>
 * mvn test -Dtest=SectorFrameSizeTest -Djunit.jupiter.conditions.deactivate='org.junit.*DisabledCondition' -DargLine=-Xmx8g
 * with -Dchaos.ticks=1500 for the length of the run.
 */
@SpringBootTest(properties = {
        "cells.size.blockSize=2000",
        "cells.size.sectorSize=50",
        "cells.pruning-per-generation=100",
        "cells.runmode=MANUAL",
        "cells.targetspeed=0",
        "logging.level.slobben=WARN"
})
class SectorFrameSizeTest {

    private static final int SIZE = 2000;
    private static final int[] SECTOR_SIZES = {50, 40, 20, 16, 10};
    private static final int SAMPLE_EVERY = 100;
    private static final int WARM_UP = 200;

    @Autowired
    private RunnerService runnerService;
    @Autowired
    private Map<String, Block> blocks;

    @Test
    @Disabled("Too expensive for normal test run")
    void sectorFrameSizes() {
        int ticks = Integer.getInteger("chaos.ticks", 1500);
        List<List<Integer>> sectorBytes = new ArrayList<>();
        for (int ignored : SECTOR_SIZES) sectorBytes.add(new ArrayList<>());
        List<Integer> borderBytes = new ArrayList<>();

        for (int tick = 1; tick <= ticks; tick++) {
            runnerService.runCycle();
            if (tick <= WARM_UP || tick % SAMPLE_EVERY != 0) continue;
            for (Block block : blocks.values()) {
                long[][] rows = block.getPackedCells();
                for (int i = 0; i < SECTOR_SIZES.length; i++) {
                    sectorBytes.get(i).add(Zstd.compress(pack(count(rows, SECTOR_SIZES[i]))).length);
                }
                EncodedBlock borders = block.getEncodedBlockBorders();
                if (borders != null) borderBytes.add(borders.encodedCells().length);
            }
            System.out.printf("tick %d: %d blocks%n", tick, blocks.size());
        }

        System.out.printf("%n%-8s %-9s %8s %8s %8s %8s %8s%n", "sector", "grid", "raw", "mean", "p50", "p90", "max");
        for (int i = 0; i < SECTOR_SIZES.length; i++) {
            int sectors = SIZE / SECTOR_SIZES[i];
            print("%d".formatted(SECTOR_SIZES[i]), "%dx%d".formatted(sectors, sectors), 2 * sectors * sectors, sectorBytes.get(i));
        }
        print("BORDER", "", (SIZE * 4 - 4 + 7) / 8, borderBytes);
    }

    private static short[][] count(long[][] rows, int sectorSize) {
        int sectors = SIZE / sectorSize;
        short[][] counts = new short[sectors][sectors];
        for (int x = 1; x <= SIZE; x++) {
            short[] band = counts[(x - 1) / sectorSize];
            long[] row = rows[x];
            for (int w = 1; w < row.length; w++) {
                for (long bits = row[w]; bits != 0; bits &= bits - 1) {
                    int col = ((w - 1) << 6) + Long.numberOfTrailingZeros(bits);
                    if (col >= 1 && col <= SIZE) band[(col - 1) / sectorSize]++;
                }
            }
        }
        return counts;
    }

    private static byte[] pack(short[][] counts) {
        ByteBuffer packed = ByteBuffer.allocate(2 * counts.length * counts.length).order(ByteOrder.LITTLE_ENDIAN);
        for (short[] band : counts) {
            for (short count : band) packed.putShort(count);
        }
        return packed.array();
    }

    private static void print(String name, String grid, int raw, List<Integer> sizes) {
        int[] sorted = sizes.stream().mapToInt(Integer::intValue).sorted().toArray();
        double mean = sizes.stream().mapToInt(Integer::intValue).average().orElse(0);
        System.out.printf("%-8s %-9s %8d %8.0f %8d %8d %8d   (n=%d)%n", name, grid, raw, mean,
                sorted[sorted.length / 2], sorted[sorted.length * 9 / 10], sorted[sorted.length - 1], sorted.length);
    }
}
