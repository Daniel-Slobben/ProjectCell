package slobben.cells.service.workers.chaos;

import slobben.cells.service.workers.chaos.makers.*;

enum ChaosType {
    SQUARE(new SquareMaker()),
    SQUARE_IN_SQUARE(new SquareInSquareMaker()),
    LETTUCE(new LettuceMaker()),
    GROWTH_PATTERN(new GrowthMaker()),
    DIAGONAL_LINES(new DiagonalMaker());

    final Maker maker;

    ChaosType(Maker maker) {
        this.maker = maker;
    }
}
