package slobben.cells.enums;

import lombok.Getter;

import java.util.Arrays;

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

    public static Direction of(int dx, int dy) {
        return Arrays.stream(values()).filter(d -> d.dx == dx && d.dy == dy).findFirst().orElseThrow();
    }

    // a quarter turn counterclockwise on screen per rotation, the way RleReader rotates patterns
    public Direction rotated(int rotation) {
        Direction direction = this;
        for (int i = 0; i < Math.floorMod(rotation, 4); i++) {
            direction = of(direction.dy, -direction.dx);
        }
        return direction;
    }
}
