package slobben.cells.util;

import slobben.cells.entities.Coordinates;

public final class BlockUtils {
    private static final String SPLIT_CHAR = "/";

    public static String getKey(int x, int y) {
        return x + SPLIT_CHAR + y;
    }

    public static Coordinates resolveKey(String key) {
        var split = key.split(SPLIT_CHAR);
        return new Coordinates(Integer.parseInt(split[0]), Integer.parseInt(split[1]));
    }
}
