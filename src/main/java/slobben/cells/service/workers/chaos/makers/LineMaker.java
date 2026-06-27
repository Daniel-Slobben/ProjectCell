package slobben.cells.service.workers.chaos.makers;

import slobben.cells.entities.Pattern;
import slobben.cells.service.workers.chaos.ChaosHit;

public class LineMaker implements Maker {
    private static final int AMOUNT_OF_LINES = 6;
    private static final int LINE_WIDTH = 2;
    private static final int LINE_SIZE = 2000;
    private static final int DISTANCE_BETWEEN_LINES = 200;

    private static final boolean VERTICALS = true;
    private static final boolean HORIZONTALS = true;

    @Override
    public ChaosHit getChaosHit(int worldTargetX, int worldTargetY) {
        boolean[][] matrix = new boolean[LINE_SIZE][LINE_SIZE];

        for (int i = 0; i < AMOUNT_OF_LINES; i++) {
            int adjustedI = i * DISTANCE_BETWEEN_LINES;
            for (int j = 0; j < LINE_SIZE; j++) {
                for (int line = 0; line < LINE_WIDTH; line++) {

                    if (VERTICALS) {
                        matrix[adjustedI + line][j] = true;
                    }
                    if (HORIZONTALS) {
                        matrix[j][adjustedI + line] = true;
                    }

                }
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
