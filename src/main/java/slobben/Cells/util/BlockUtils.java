package slobben.Cells.util;

import org.springframework.data.util.Pair;

public class BlockUtils {
    private static final String SPLIT_CHAR = "/";

    public static String getKey(int x, int y) {
        return x + SPLIT_CHAR + y;
    }

    public static Pair<Integer, Integer> resolveKey(String key) {
        var split = key.split(SPLIT_CHAR);
        return Pair.of(Integer.parseInt(split[0]), Integer.parseInt(split[1]));
    }
}
