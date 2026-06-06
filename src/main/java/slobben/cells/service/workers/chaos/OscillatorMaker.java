package slobben.cells.service.workers.chaos;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import slobben.cells.entities.Pattern;
import slobben.cells.util.RleReader;

import java.util.Random;

@Component
@RequiredArgsConstructor
@Slf4j
class OscillatorMaker {
    private static final Random random = new Random();
    private static final int DISTANCE_BETWEEN_PATTERN_MIN = 100;
    private static final int DISTANCE_BETWEEN_PATTERN_MAX = 250;
    private static final int RANDOM_OFFSET_RANGE = 40;
    private final RleReader rleReader;
    @Value("${cells.chaos.square-size-min}")
    int squareSizeMin;
    @Value("${cells.chaos.square-size-max}")
    int squareSizeMax;

    public ChaosHit getChaosHit(int worldTargetX, int worldTargetY) {
        int squareSize = random.nextInt(squareSizeMin, squareSizeMax);
        boolean[][] matrix = new boolean[squareSize][squareSize];
        boolean matrixFull = false;

        int xOffset = 0;
        int yOffset = 0;
        int adjustedXOffset = 0;
        int adjustedYOffset = 0;

        while (!matrixFull) {
            try {
                Pattern patternToAdd = rleReader.readRandomPatternFromCategory("oscillators");
                if (patternToAdd.x() < squareSize / 4 && patternToAdd.y() < squareSize / 4) {
                    xOffset += patternToAdd.x() + random.nextInt(DISTANCE_BETWEEN_PATTERN_MIN, DISTANCE_BETWEEN_PATTERN_MAX);
                    if (xOffset > matrix.length) {
                        xOffset = 0;
                        yOffset += patternToAdd.y() + random.nextInt(DISTANCE_BETWEEN_PATTERN_MIN, DISTANCE_BETWEEN_PATTERN_MAX);
                        if (yOffset > matrix[0].length) {
                            matrixFull = true;
                            worldTargetX += adjustedXOffset;
                            worldTargetY += adjustedYOffset;
                        }
                    }
                    adjustedXOffset = xOffset + random.nextInt(-RANDOM_OFFSET_RANGE, RANDOM_OFFSET_RANGE);
                    adjustedYOffset = yOffset + random.nextInt(-RANDOM_OFFSET_RANGE, RANDOM_OFFSET_RANGE);
                    for (int x = 0; x < patternToAdd.matrix().length; x++) {
                        System.arraycopy(patternToAdd.matrix()[x], 0, matrix[x + adjustedXOffset], adjustedYOffset, patternToAdd.matrix()[0].length);
                    }
                }
            } catch (Exception e) {
                log.warn("Continue after rleReader Exception: " + e.getMessage());
            }
        }

        Pattern pattern = Pattern.builder()
                .x(matrix.length)
                .y(matrix[0].length)
                .matrix(matrix)
                .build();
        return new ChaosHit(worldTargetX, worldTargetY, "Random oscillator patterns with size " + squareSize, pattern);
    }
}
