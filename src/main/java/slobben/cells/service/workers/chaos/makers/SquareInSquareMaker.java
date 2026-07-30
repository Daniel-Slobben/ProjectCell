package slobben.cells.service.workers.chaos.makers;

import slobben.cells.entities.Pattern;
import slobben.cells.service.workers.chaos.ChaosHit;

import java.util.Random;

public class SquareInSquareMaker implements Maker {
    private static final int SQUARE_SIZE_MIN = 2000;
    private static final int SQUARE_SIZE_MAX = 4000;
    private static final int STARTING_DISTANCE_MIN = 25;
    private static final int STARTING_DISTANCE_MAX = 100;
    private static final double GROWTH_MULTIPLIER = 1.0;

    private final Random random = new Random();

    @Override
    public ChaosHit getChaosHit(int worldTargetX, int worldTargetY) {
        final int maxSquareSize = random.nextInt(SQUARE_SIZE_MIN, SQUARE_SIZE_MAX);
        boolean[][] matrix = new boolean[maxSquareSize][maxSquareSize];

        int squareSize = random.nextInt(STARTING_DISTANCE_MIN, STARTING_DISTANCE_MAX);

        while (true) {
            int distanceFromBorder = (maxSquareSize / 2) - (squareSize / 2);
            if (distanceFromBorder < 0) {
                break;
            }
            for (int i = 0; i < squareSize; i++) {
                int adjustedI = i + distanceFromBorder;
                matrix[distanceFromBorder][adjustedI] = true;
                matrix[1 + distanceFromBorder][adjustedI] = true;

                matrix[maxSquareSize - 1 - distanceFromBorder][adjustedI] = true;
                matrix[maxSquareSize - 2 - distanceFromBorder][adjustedI] = true;

                matrix[adjustedI][distanceFromBorder] = true;
                matrix[adjustedI][1 + distanceFromBorder] = true;

                matrix[adjustedI][maxSquareSize - 1 - distanceFromBorder] = true;
                matrix[adjustedI][maxSquareSize - 2 - distanceFromBorder] = true;
            }
            squareSize = squareSize + (int) (squareSize * GROWTH_MULTIPLIER);
        }
        Pattern pattern = Pattern.builder()
                .x(matrix.length)
                .y(matrix[0].length)
                .matrix(matrix)
                .build();
        return new ChaosHit(worldTargetX + maxSquareSize / 2, worldTargetY + maxSquareSize / 2, "Square with size " + maxSquareSize, pattern);
    }
}
