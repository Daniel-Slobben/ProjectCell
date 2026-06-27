package slobben.cells.service.workers.chaos.makers;

import slobben.cells.entities.Pattern;
import slobben.cells.service.workers.chaos.ChaosHit;

public interface Maker {
    default void addPatternToMatrix(Pattern growthPattern, boolean[][] matrix, int xOffset, int yOffset) {
        try {
            for (int x = 0; x < growthPattern.matrix().length; x++) {
                for (int y = 0; y < growthPattern.matrix()[0].length; y++) {
                    // only set true cells
                    if (growthPattern.matrix()[x][y]) {
                        matrix[x + xOffset][y + yOffset] = true;
                    }
                }
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new IllegalArgumentException("growthPattern with x: %s + offsetX: %s, y: %s + offset: %s, doesnt fit a matrix of size: x: %s, y: %s"
                    .formatted(growthPattern.x(), xOffset, growthPattern.y(), yOffset, matrix.length, matrix[0].length),
                    e.getCause());
        }
    }

    ChaosHit getChaosHit(int worldTargetX, int worldTargetY);
}
