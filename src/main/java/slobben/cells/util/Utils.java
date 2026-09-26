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
        int blockX = Math.floorDiv(x, blockSize);
        int blockY = Math.floorDiv(y, blockSize);
        int relativeCellX = Math.floorMod(x, blockSize);
        if (relativeCellX < 0) {
            relativeCellX = relativeCellX + blockSize;
        }
        int relativeCellY = Math.floorMod(y, blockSize);
        if (relativeCellY < 0) {
            relativeCellY = relativeCellY + blockSize;
        }
        return new BlockCoordinatesResult(blockX, blockY, relativeCellX, relativeCellY);
    }


}
