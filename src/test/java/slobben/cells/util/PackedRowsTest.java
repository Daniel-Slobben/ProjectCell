package slobben.cells.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class PackedRowsTest {

    @ParameterizedTest
    @ValueSource(ints = {1, 62, 63, 64, 65, 127, 128, 200})
    void packKeepsEveryCell(int size) {
        // prepare
        Random random = new Random(size);
        boolean[][] cells = new boolean[size + 2][size + 2];
        for (int x = 0; x < cells.length; x++) {
            // empty rows, full rows and rows in between, the borders included
            int density = x % 4 == 0 ? 0 : x % 4 == 1 ? 100 : random.nextInt(100);
            for (int y = 0; y < cells.length; y++) {
                cells[x][y] = random.nextInt(100) < density;
            }
        }

        // execute
        long[][] packed = PackedRows.pack(cells);

        // verify
        for (int x = 0; x < cells.length; x++) {
            for (int y = 0; y < cells.length; y++) {
                assertThat(PackedRows.get(packed[x], y)).as("row %d column %d", x, y).isEqualTo(cells[x][y]);
            }
            assertThat(packed[x][0]).isZero();
            assertThat(packed[x][packed[x].length - 1]).isZero();
        }
    }
}
