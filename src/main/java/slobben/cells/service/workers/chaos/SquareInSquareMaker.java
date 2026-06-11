package slobben.cells.service.workers.chaos;

import org.springframework.stereotype.Component;
import slobben.cells.entities.Pattern;

@Component
public class SquareInSquareMaker {
    private static final int SQUARE_SIZE = 2500;
    private static final int DISTANCE_BETWEEN_SQUARES = 300;
    private static final int AMOUNT_OF_SQUARES = 7;

    public ChaosHit getChaosHit(int worldTargetX, int worldTargetY) {
        boolean[][] matrix = new boolean[SQUARE_SIZE][SQUARE_SIZE];

        for (int k = AMOUNT_OF_SQUARES; k >= 0; k--) {
            int squareSize = SQUARE_SIZE - (k * DISTANCE_BETWEEN_SQUARES);
            int distanceFromBorder = Math.abs(squareSize - SQUARE_SIZE) / 2;
            for (int i = 0; i < squareSize; i++) {
                int adjustedI = i + distanceFromBorder;

                matrix[distanceFromBorder][adjustedI] = true;
                matrix[1 + distanceFromBorder][adjustedI] = true;

                matrix[SQUARE_SIZE - 1 - distanceFromBorder][adjustedI] = true;
                matrix[SQUARE_SIZE - 2 - distanceFromBorder][adjustedI] = true;

                matrix[adjustedI][distanceFromBorder] = true;
                matrix[adjustedI][1 + distanceFromBorder] = true;

                matrix[adjustedI][SQUARE_SIZE - 1 - distanceFromBorder] = true;
                matrix[adjustedI][SQUARE_SIZE - 2 - distanceFromBorder] = true;
            }

        }
        Pattern pattern = Pattern.builder()
                .x(matrix.length)
                .y(matrix[0].length)
                .matrix(matrix)
                .build();
        return new ChaosHit(worldTargetX, worldTargetY, "Square with size " + SQUARE_SIZE, pattern);
    }
}
