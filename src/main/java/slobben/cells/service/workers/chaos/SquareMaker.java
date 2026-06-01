package slobben.cells.service.workers.chaos;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import slobben.cells.entities.Pattern;

import java.util.Random;

@Component
class SquareMaker {

    private static final Random random = new Random();
    @Value("${cells.chaos.square-size-min}")
    int squareSizeMin;
    @Value("${cells.chaos.square-size-max}")
    int squareSizeMax;

    public ChaosHit getChaosHit(int worldTargetX, int worldTargetY) {
        int squareSize = random.nextInt(squareSizeMin, squareSizeMax);
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
