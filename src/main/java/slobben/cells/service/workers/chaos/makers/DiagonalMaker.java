package slobben.cells.service.workers.chaos.makers;

import lombok.RequiredArgsConstructor;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;
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
        final int squareSize = random.nextInt(MIN_SIZE, MAX_SIZE);
        final int startX = squareSize / 2;
        final int startY = squareSize / 2;

        setCells(startX, startY, squareSize, chaosHitId);

        final int centerX = startX + (squareSize - 1) / 2;
        final int centerY = startY + (squareSize - 1) / 2;

        IntFunction<Pair<Integer, Integer>> viewCalculator = getViewCalculator(centerX, centerY);

        return new ChaosHit("Diagonal cross " + squareSize + " pixels wide", viewCalculator, squareSize * 6);
    }

    private IntFunction<Pair<Integer, Integer>> getViewCalculator(final int centerX, final int centerY) {
        return age -> switch (CornerEnum.getRandomCorner()) {
            case TOP_LEFT -> Pair.of(centerX - age, centerY - age);
            case TOP_RIGHT -> Pair.of(centerX + age, centerY - age);
            case BOTTOM_LEFT -> Pair.of(centerX - age, centerY + age);
            case BOTTOM_RIGHT -> Pair.of(centerX + age, centerY + age);
        };
    }

    private void setCells(int startX, int startY, int squareSize, UUID hitId) {
        for (int i = 0; i < squareSize; i++) {
            worldEditor.setCell(startX + i, startY + i, hitId);
            worldEditor.setCell(startX + squareSize - 1 - i, startY + i, hitId);
        }
    }
}
