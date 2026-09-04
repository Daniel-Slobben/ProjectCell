package slobben.cells.service.workers.chaos.makers;

import slobben.cells.entities.Pattern;
import slobben.cells.service.workers.chaos.ChaosHit;

import java.util.Random;

import static slobben.cells.util.Utils.makeEven;

public class LettuceMaker implements Maker {
    private static final int MIN_AMOUNT_OF_LINES = 4;
    private static final int MAX_AMOUNT_OF_LINES = 12;
    private static final int LINE_WIDTH = 2;
    private static final int MIN_SIZE = 1500;
    private static final int MAX_SIZE = 3000;

    private static final Random random = new Random();

    private static int getIntersectionInteger(int amountOfLines) {
        return random.nextInt(1, amountOfLines - 1);
    }

    @Override
    public ChaosHit getChaosHit(int worldTargetX, int worldTargetY) {
        int amountOfLines = random.nextInt(MIN_AMOUNT_OF_LINES, MAX_AMOUNT_OF_LINES + 1);
        int size = makeEven(random.nextInt(MIN_SIZE, MAX_SIZE + 1));
        int distanceBetweenLines = size / amountOfLines;
        size -= distanceBetweenLines;
        size += 2;

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
        return new ChaosHit(worldTargetX + (distanceBetweenLines * getIntersectionInteger(amountOfLines)), worldTargetY + (distanceBetweenLines * getIntersectionInteger(amountOfLines)), "LINES with size " + size, pattern);
    }
}
