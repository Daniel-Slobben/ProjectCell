package slobben.cells.enums;

import java.util.Random;

public enum CornerEnum {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT;

    private static final Random RANDOM = new Random();

    public static CornerEnum getRandomCorner() {
        return CornerEnum.values()[RANDOM.nextInt(CornerEnum.values().length)];
    }
}
