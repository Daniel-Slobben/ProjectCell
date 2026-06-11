package slobben.cells.service.workers.chaos;

import org.springframework.stereotype.Component;
import slobben.cells.entities.Pattern;

@Component
public class LineMaker {
    private static final int AMOUNT_OF_LINES = 4;
    private static final int LINE_WIDTH = 2;
    private static final int LINE_SIZE = 2000;
    private static final int DISTANCE_BETWEEN_LINES = 200;

    public ChaosHit getChaosHit(int worldTargetX, int worldTargetY) {
        boolean[][] matrix = new boolean[LINE_SIZE][LINE_SIZE];
        for (int i = 0; i < AMOUNT_OF_LINES; i++) {
            int adjustedI = i * DISTANCE_BETWEEN_LINES;
            for (int j = 0; j < LINE_SIZE; j++) {
                matrix[adjustedI][j] = true;
                matrix[adjustedI + 1][j] = true;
            }
        }
        Pattern pattern = Pattern.builder()
                .x(matrix.length)
                .y(matrix[0].length)
                .matrix(matrix)
                .build();
        return new ChaosHit(worldTargetX, worldTargetY, "LINES with size " + LINE_SIZE, pattern);
    }
}
