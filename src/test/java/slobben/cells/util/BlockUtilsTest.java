package slobben.cells.util;

import org.junit.jupiter.api.Test;
import slobben.cells.dto.internal.Coordinates;

import static org.assertj.core.api.Assertions.assertThat;

class BlockUtilsTest {

    @Test
    void keyToBigKey() {
        // prepare
        int factor = 2;

        String keyTopLeft = BlockUtils.getKey(0, 0);
        String keyTopRight = BlockUtils.getKey(1, 0);
        String keyBottomLeft = BlockUtils.getKey(0, 1);
        String keyBottomRight = BlockUtils.getKey(1, 1);

        // execute
        String bigTopLeft = BlockUtils.keyToBigKey(keyTopLeft, factor);
        String bigTopRight = BlockUtils.keyToBigKey(keyTopRight, factor);
        String bigBottomLeft = BlockUtils.keyToBigKey(keyBottomLeft, factor);
        String bigBottomRight = BlockUtils.keyToBigKey(keyBottomRight, factor);
        String bigBottomRight2 = BlockUtils.keyToBigKey(BlockUtils.getKey(2, 1), factor);

        // verify
        assertThat(bigTopLeft).isEqualTo(bigTopRight).isEqualTo(bigBottomLeft).isEqualTo(bigBottomRight).isNotEqualTo(bigBottomRight2);
    }

    @Test
    void keyToBigKeyCorner() {
        // prepare
        int factor = 2;

        String keyTopLeft = BlockUtils.getKey(0, 0);
        String keyTopRight = BlockUtils.getKey(0, 1);
        String keyBottomLeft = BlockUtils.getKey(1, 0);
        String keyBottomRight = BlockUtils.getKey(1, 1);

        String keyTopLeft2 = BlockUtils.getKey(4, 4);

        // execute
        Coordinates bigTopLeft = BlockUtils.keyToBigKeyCorner(keyTopLeft, factor);
        Coordinates bigTopRight = BlockUtils.keyToBigKeyCorner(keyTopRight, factor);
        Coordinates bigBottomLeft = BlockUtils.keyToBigKeyCorner(keyBottomLeft, factor);
        Coordinates bigBottomRight = BlockUtils.keyToBigKeyCorner(keyBottomRight, factor);
        Coordinates bigTopLeft2 = BlockUtils.keyToBigKeyCorner(keyTopLeft2, factor);

        // verify
        assertThat(bigTopLeft.x()).isZero();
        assertThat(bigTopLeft.y()).isZero();

        assertThat(bigTopRight.x()).isZero();
        assertThat(bigTopRight.y()).isOne();

        assertThat(bigBottomLeft.x()).isOne();
        assertThat(bigBottomLeft.y()).isZero();

        assertThat(bigBottomRight.x()).isOne();
        assertThat(bigBottomRight.y()).isOne();

        assertThat(bigTopLeft.x()).isEqualTo(bigTopLeft2.x());
        assertThat(bigTopLeft.y()).isEqualTo(bigTopLeft2.y());
    }
}