package slobben.cells.util;

import slobben.cells.dto.internal.Coordinates;
import slobben.cells.entities.Block;

import java.util.Random;

public final class BlockUtils {
    private static final String SPLIT_CHAR = "/";
    private static final Random random = new Random();

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


    public static Block setBlockToRandom(Block block, int cellPopulation, int blockSize) {
        for (int x = 0; x < blockSize; x++) {
            for (int y = 0; y < blockSize; y++) {
                if (random.nextInt(0, cellPopulation) == 0) {
                    block.getCells()[x][y] = true;
                }
            }
        }
        return block;
    }

}
