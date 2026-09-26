package slobben.cells.service.workers.chaos.makers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

        IntFunction<Coordinates> viewCalculator = getViewCalculator(startX, startY, size);

        return new ChaosHit("2 pixels thick square " + size + " pixels wide", viewCalculator, size);
    }

    private IntFunction<Coordinates> getViewCalculator(final int startX, final int startY, final int size) {
        return age -> {
            age = Math.min(age, size / 2);
            return switch (CornerEnum.getRandomCorner()) {
                case TOP_LEFT -> new Coordinates(startX + age, startY + age);
                case TOP_RIGHT -> new Coordinates(startX + size - age, startY + age);
                case BOTTOM_LEFT -> new Coordinates(startX + age, startY + size - age);
                case BOTTOM_RIGHT -> new Coordinates(startX + size - age, startY + size - age);
            };
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
