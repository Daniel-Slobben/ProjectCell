package slobben.cells.service.workers.chaos.makers;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import slobben.cells.dto.internal.Coordinates;
import slobben.cells.enums.CornerEnum;
import slobben.cells.enums.Direction;
import slobben.cells.service.WorldEditor;
import slobben.cells.service.workers.chaos.ChaosHit;

import java.util.Random;
import java.util.UUID;
import java.util.function.IntFunction;

@Component
@RequiredArgsConstructor
public class DiagonalMaker implements Maker {

    private static final Random random = new Random();
    private static final int MIN_SIZE = 45000;
    private static final int MAX_SIZE = 60000;

    private final WorldEditor worldEditor;

    @Override
    public ChaosHit getChaosHit(int worldX, int worldY) {
        UUID chaosHitId = UUID.randomUUID();
        final int size = random.nextInt(MIN_SIZE, MAX_SIZE);
        final int startX = (-(size / 2)) + worldX;
        final int startY = (-(size / 2)) + worldY;

        setCells(startX, startY, size, chaosHitId);

        final int centerX = startX + (size - 1) / 2;
        final int centerY = startY + (size - 1) / 2;

        IntFunction<Coordinates> viewCalculator = getViewCalculator(centerX, centerY, size);

        return new ChaosHit("Diagonal cross " + size + " pixels wide", viewCalculator, size / 3);
    }

    private IntFunction<Coordinates> getViewCalculator(final int centerX, final int centerY, final int size) {
        return age -> {
            int cornerOffset = Math.min(age, size / 4) - 30;
            int middleOffset = age / 2;

            return switch (Direction.getRandomDirection()) {
                case LOW_X_LOW_Y -> new Coordinates(centerX - cornerOffset, centerY - cornerOffset);
                case LOW_X_MID_Y -> new Coordinates(centerX - middleOffset, centerY);
                case LOW_X_HIGH_Y -> new Coordinates(centerX + cornerOffset, centerY - cornerOffset);
                case MID_X_LOW_Y -> new Coordinates(centerX, centerY - middleOffset);
                case MID_X_HIGH_Y -> new Coordinates(centerX, centerY + middleOffset);
                case HIGH_X_LOW_Y -> new Coordinates(centerX - cornerOffset, centerY + cornerOffset);
                case HIGH_X_MID_Y -> new Coordinates(centerX + middleOffset, centerY);
                case HIGH_X_HIGH_Y -> new Coordinates(centerX + cornerOffset, centerY + cornerOffset);
            };
        };
    }

    private void setCells(int startX, int startY, int squareSize, UUID hitId) {
        for (int i = 0; i < squareSize; i++) {
            worldEditor.setCell(startX + i, startY + i, hitId);
            worldEditor.setCell(startX + squareSize - 1 - i, startY + i, hitId);
        }
    }
}
