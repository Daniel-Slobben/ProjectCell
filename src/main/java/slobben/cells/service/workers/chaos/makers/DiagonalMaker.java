package slobben.cells.service.workers.chaos.makers;

import slobben.cells.entities.Pattern;
import slobben.cells.service.workers.chaos.ChaosHit;

import java.util.Random;

public class DiagonalMaker implements Maker {

    private static final Random random = new Random();
    private static final int MIN_SIZE = 500;
    private static final int MAX_SIZE = 2500;

    @Override
    public ChaosHit getChaosHit(int worldTargetX, int worldTargetY) {
        int squareSize = random.nextInt(MIN_SIZE, MAX_SIZE);
        boolean[][] matrix = new boolean[squareSize][squareSize];
        for (int i = 0; i < squareSize; i++) {
            matrix[i][i] = true;
            matrix[squareSize - i - 1][i] = true;
        }
        Pattern pattern = Pattern.builder().x(matrix.length).y(matrix[0].length).matrix(matrix).build();

        worldTargetX = worldTargetX + squareSize / 2;
        worldTargetY = worldTargetY + squareSize / 2;

        return new ChaosHit(worldTargetX, worldTargetY, "Diagonal", pattern);
    }
}
