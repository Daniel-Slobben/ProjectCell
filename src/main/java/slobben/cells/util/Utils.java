package slobben.cells.util;

import org.jspecify.annotations.NonNull;

public class Utils {
    public static int makeEven(int numberToMakeEven) {
        if (numberToMakeEven % 2 == 0) {
            return numberToMakeEven;
        }
        return numberToMakeEven - 1;
    }

    public static @NonNull BlockCoordinatesResult getBlockCoordinates(int x, int y, int blockSize) {
        int blockX = x / blockSize;
        int blockY = y / blockSize;
        int relativeCellX = Math.toIntExact(x % blockSize);
        if (relativeCellX < 0) {
            relativeCellX = relativeCellX + blockSize;
        }
        int relativeCellY = Math.toIntExact(y % blockSize);
        if (relativeCellY < 0) {
            relativeCellY = relativeCellY + blockSize;
        }
        return new BlockCoordinatesResult(blockX, blockY, relativeCellX, relativeCellY);
    }


}
