package slobben.cells.enums;

import slobben.cells.service.workers.chaosmakers.*;

import java.util.Arrays;
import java.util.Random;

public enum ChaosType {
    SQUARE(SquareMaker.class, 3),
    LETTUCE(LettuceMaker.class, 3),
    GROWTH_PATTERN(GrowthMaker.class, 2),
    SPACESHIP(SpaceshipMaker.class, 5),
    DIAGONAL_LINES(DiagonalMaker.class, 3);

    private static final Random random = new Random();
    private static final int TOTAL_WEIGHT = Arrays.stream(values()).mapToInt(type -> type.weight).sum();

    public final Class<? extends Maker> maker;
    // higher weight -> higher chance to getRandomType
    public final int weight;

    ChaosType(Class<? extends Maker> maker, int weight) {
        this.maker = maker;
        this.weight = weight;
    }

    public static ChaosType getRandomType() {
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
