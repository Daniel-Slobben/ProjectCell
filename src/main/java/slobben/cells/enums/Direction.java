package slobben.cells.enums;

import lombok.Getter;

import java.util.concurrent.ThreadLocalRandom;

@Getter
public enum Direction {
    LOW_X_LOW_Y(-1, -1),
    LOW_X_MID_Y(-1, 0),
    LOW_X_HIGH_Y(-1, 1),
    MID_X_LOW_Y(0, -1),
    MID_X_HIGH_Y(0, 1),
    HIGH_X_LOW_Y(1, -1),
    HIGH_X_MID_Y(1, 0),
    HIGH_X_HIGH_Y(1, 1);

    private final int dx;
    private final int dy;

    Direction(int dx, int dy) {
        this.dx = dx;
        this.dy = dy;
    }

    public static Direction from(int i, int j) {
        for (Direction dir : values()) {
            if (dir.dx == i && dir.dy == j) {
                return dir;
            }
        }
        return null;
    }

    public static Direction getRandomDirection() {
        return values()[ThreadLocalRandom.current().nextInt(values().length)];
    }
}
