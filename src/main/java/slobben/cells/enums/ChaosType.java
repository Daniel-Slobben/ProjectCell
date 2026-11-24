package slobben.cells.enums;

import slobben.cells.service.workers.chaosmakers.*;

public enum ChaosType {
    SQUARE(SquareMaker.class),
    DIAGONAL_LETTUCE(DiagonalLettuceMaker.class),
    LETTUCE(LettuceMaker.class),
    DIAGONAL_LINES(DiagonalMaker.class),
    GROWTH_PATTERN(GrowthMaker.class),
    SPACESHIP(SpaceshipMaker.class);

    public final Class<? extends Maker> maker;

    ChaosType(Class<? extends Maker> maker) {
        this.maker = maker;
    }
}
