package slobben.cells.service.workers.chaos.makers;

import slobben.cells.entities.Pattern;
import slobben.cells.service.workers.chaos.ChaosHit;

import java.util.Random;

public class SquareMaker implements Maker {

    private static final Random random = new Random();
    private static final int MIN_SIZE = 600;
    private static final int MAX_SIZE = 1200;

    @Override
    public ChaosHit getChaosHit(int worldTargetX, int worldTargetY) {
        int squareSize = random.nextInt(MIN_SIZE, MAX_SIZE);
        boolean[][] matrix = new boolean[squareSize][squareSize];
        for (int i = 0; i < squareSize; i++) {
            matrix[0][i] = true;
            matrix[1][i] = true;

            matrix[squareSize - 1][i] = true;
            matrix[squareSize - 2][i] = true;

            matrix[i][0] = true;
            matrix[i][1] = true;

            matrix[i][squareSize - 1] = true;
            matrix[i][squareSize - 2] = true;
        }
        Pattern pattern = Pattern.builder()
                .x(matrix.length)
                .y(matrix[0].length)
                .matrix(matrix)
                .build();
        return new ChaosHit(worldTargetX, worldTargetY, "Square with size " + squareSize, pattern);
    }
}
