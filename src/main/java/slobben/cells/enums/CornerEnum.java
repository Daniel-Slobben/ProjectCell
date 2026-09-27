package slobben.cells.enums;

import java.util.concurrent.ThreadLocalRandom;

public enum CornerEnum {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT;

    public static CornerEnum getRandomCorner() {
        CornerEnum[] values = CornerEnum.values();
        return values[ThreadLocalRandom.current().nextInt(values.length)];
    }
}
