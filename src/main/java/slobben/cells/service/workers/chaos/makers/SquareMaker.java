package slobben.cells.service.workers.chaos.makers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class SquareMaker implements Maker {

    private static final Random random = new Random();
    private static final int MIN_SIZE = 600;
    private static final int MAX_SIZE = 4000;
    private final WorldEditor worldEditor;

    private int size = 0;

    @Override
    public ChaosHit getChaosHit() {
        UUID chaosHitId = UUID.randomUUID();
        size = random.nextInt(MIN_SIZE, MAX_SIZE);

        final int startX = size / 2;
        final int startY = size / 2;

        setCells(startX, startY, chaosHitId);

        IntFunction<Pair<Integer, Integer>> viewCalculator = getViewCalculator(startX, startY);

        return new ChaosHit("2 pixels thick square " + size + " pixels wide", viewCalculator, size * 6);
    }

    private IntFunction<Pair<Integer, Integer>> getViewCalculator(final int startX, final int startY) {
        return age -> switch (CornerEnum.getRandomCorner()) {
            case TOP_LEFT -> Pair.of(startX + age, startY + age);
            case TOP_RIGHT -> Pair.of(startX + size - age, startY + age);
            case BOTTOM_LEFT -> Pair.of(startX + age, startY + size - age);
            case BOTTOM_RIGHT -> Pair.of(startX + size - age, startY + size - age);
        };
    }

    private void setCells(int startX, int startY, UUID hitId) {
        for (int i = 0; i < size; i++) {
            worldEditor.setCell(startX, startY + i, hitId);
            worldEditor.setCell(startX + 1, startY + i, hitId);

            worldEditor.setCell(startX + size - 1, startY + i, hitId);
            worldEditor.setCell(startX + size - 2, startY + i, hitId);

            worldEditor.setCell(startX + i, startY, hitId);
            worldEditor.setCell(startX + i, startY + 1, hitId);

            worldEditor.setCell(startX + i, startY + size - 1, hitId);
            worldEditor.setCell(startX + i, startY + size - 2, hitId);
        }
    }
}
