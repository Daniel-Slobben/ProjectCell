package slobben.cells.enums;

import slobben.cells.service.workers.chaosmakers.*;

import java.util.Arrays;
import java.util.Random;

public enum ChaosType {
    SQUARE(SquareMaker.class, 3),
    DIAGONAL_LETTUCE(DiagonalLettuceMaker.class, 3),
    LETTUCE(LettuceMaker.class, 3),
    DIAGONAL_LINES(DiagonalMaker.class, 3),
    GROWTH_PATTERN(GrowthMaker.class, 8),
    SPACESHIP(SpaceshipMaker.class, 8);

    private static final int TOTAL_WEIGHT = Arrays.stream(values()).mapToInt(type -> type.weight).sum();

    public final Class<? extends Maker> maker;
    // higher weight -> higher chance to getRandomType
    public final int weight;

    ChaosType(Class<? extends Maker> maker, int weight) {
        this.maker = maker;
        this.weight = weight;
    }

    public static ChaosType getRandomType(Random random) {
        int roll = random.nextInt(TOTAL_WEIGHT);
        for (ChaosType type : values()) {
            roll -= type.weight;
            if (roll < 0) {
                return type;
            }
        }
        throw new IllegalStateException("Roll exceeded total weight " + TOTAL_WEIGHT);
    }
}
