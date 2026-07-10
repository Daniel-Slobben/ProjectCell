package slobben.cells.service.workers.chaos.makers;

import slobben.cells.entities.Pattern;
import slobben.cells.service.workers.chaos.ChaosHit;

import java.util.Random;

public class LineMaker implements Maker {
    private static final int MIN_AMOUNT_OF_LINES = 4;
    private static final int MAX_AMOUNT_OF_LINES = 8;
    private static final int LINE_WIDTH = 2;
    private static final int SIZE = 2000;
    private static final int MIN_DISTANCE_BETWEEN_LINES = 100;
    private static final int MAX_DISTANCE_BETWEEN_LINES = 400;

    private static final Random random = new Random();

    @Override
    public ChaosHit getChaosHit(int worldTargetX, int worldTargetY) {
        int amountOfLines = random.nextInt(MIN_AMOUNT_OF_LINES, MAX_AMOUNT_OF_LINES + 1);
        int distanceBetweenLines = random.nextInt(MIN_DISTANCE_BETWEEN_LINES, MAX_DISTANCE_BETWEEN_LINES + 1);
        int size = SIZE;
        int lineAmountLength = amountOfLines * distanceBetweenLines;
        if (amountOfLines * distanceBetweenLines > size) {
            size = lineAmountLength;
        }

        boolean[][] matrix = new boolean[size][size];

        for (int i = 0; i < amountOfLines; i++) {
            int adjustedI = i * distanceBetweenLines;
            for (int j = 0; j < size; j++) {
                for (int line = 0; line < LINE_WIDTH; line++) {
                    // verticals
                    matrix[adjustedI + line][j] = true;
                    // horizontals
                    matrix[j][adjustedI + line] = true;

                }
            }
        }

        Pattern pattern = Pattern.builder().x(matrix.length).y(matrix[0].length).matrix(matrix).build();
        return new ChaosHit(worldTargetX, worldTargetY, "LINES with size " + size, pattern);
    }
}
