package slobben.cells.service.workers.chaos.makers;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import slobben.cells.entities.Coordinates;
import slobben.cells.enums.CornerEnum;
import slobben.cells.service.WorldEditor;
import slobben.cells.service.workers.chaos.ChaosHit;

import java.util.Random;
import java.util.UUID;
import java.util.function.IntFunction;

@Component
@RequiredArgsConstructor
public class DiagonalMaker implements Maker {

    private static final Random random = new Random();
    private static final int MIN_SIZE = 60000;
    private static final int MAX_SIZE = 90000;

    private final WorldEditor worldEditor;

    @Override
    public ChaosHit getChaosHit() {
        UUID chaosHitId = UUID.randomUUID();
        final int size = random.nextInt(MIN_SIZE, MAX_SIZE);
        final int startX = size / 2;
        final int startY = size / 2;

        setCells(startX, startY, size, chaosHitId);

        final int centerX = startX + (size - 1) / 2;
        final int centerY = startY + (size - 1) / 2;

        IntFunction<Coordinates> viewCalculator = getViewCalculator(centerX, centerY, size);

        return new ChaosHit("Diagonal cross " + size + " pixels wide", viewCalculator, size);
    }

    private IntFunction<Coordinates> getViewCalculator(final int centerX, final int centerY, final int size) {
        return age -> {
            int adjustedAge = Math.min(age, size / 2);
            return switch (CornerEnum.getRandomCorner()) {
                case TOP_LEFT -> new Coordinates(centerX - adjustedAge, centerY - adjustedAge);
                case TOP_RIGHT -> new Coordinates(centerX + adjustedAge, centerY - adjustedAge);
                case BOTTOM_LEFT -> new Coordinates(centerX - adjustedAge, centerY + adjustedAge);
                case BOTTOM_RIGHT -> new Coordinates(centerX + adjustedAge, centerY + adjustedAge);
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
