package slobben.cells.util;

import slobben.cells.entities.Coordinates;

public final class BlockUtils {
    private static final String SPLIT_CHAR = "/";

    public static String getKey(int x, int y) {
        return (x + SPLIT_CHAR + y);
    }

    public static Coordinates resolveKey(String key) {
        var split = key.split(SPLIT_CHAR);
        return new Coordinates(Integer.parseInt(split[0]), Integer.parseInt(split[1]));
    }

    public static String keyToBigKey(String key, int factor) {
        var split = key.split(SPLIT_CHAR);
        int bigX = Math.floorDiv(Integer.parseInt(split[0]), factor);
        int bigY = Math.floorDiv(Integer.parseInt(split[1]), factor);

        return getKey(bigX, bigY);
    }

    public static Coordinates keyToBigKeyCorner(String key, int factor) {
        var split = key.split(SPLIT_CHAR);
        int relativeX = Math.floorMod(Integer.parseInt(split[0]), factor);
        int relativeY = Math.floorMod(Integer.parseInt(split[1]), factor);

        return new Coordinates(relativeX, relativeY);
    }
}
