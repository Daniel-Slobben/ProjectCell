package slobben.Cells.enums;

import lombok.Getter;

@Getter
public enum Direction {
    TOP_LEFT(-1, -1),
    TOP(-1, 0),
    TOP_RIGHT(-1, 1),
    LEFT(0, -1),
    RIGHT(0, 1),
    BOTTOM_LEFT(1, -1),
    BOTTOM(1, 0),
    BOTTOM_RIGHT(1, 1);

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
}
