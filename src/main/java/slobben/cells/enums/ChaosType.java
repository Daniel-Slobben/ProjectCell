package slobben.cells.enums;

import slobben.cells.service.workers.chaosmakers.*;

public enum ChaosType {
    SQUARE(SquareMaker.class, 3),
    DIAGONAL_LETTUCE(DiagonalLettuceMaker.class, 3),
    LETTUCE(LettuceMaker.class, 3),
    DIAGONAL_LINES(DiagonalMaker.class, 3),
    GROWTH_PATTERN(GrowthMaker.class, 8),
    SPACESHIP(SpaceshipMaker.class, 8);

    public final Class<? extends Maker> maker;
    // higher weight -> higher chance to ChaosCycle.getRandomType
    public final int weight;

    ChaosType(Class<? extends Maker> maker, int weight) {
        this.maker = maker;
        this.weight = weight;
    }
}
