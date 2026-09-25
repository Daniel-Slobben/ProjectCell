package slobben.cells.service.workers.chaos;

import slobben.cells.service.workers.chaos.makers.DiagonalMaker;
import slobben.cells.service.workers.chaos.makers.Maker;
import slobben.cells.service.workers.chaos.makers.SquareMaker;

enum ChaosType {
    SQUARE(SquareMaker.class),
    //    SQUARE_IN_SQUARE(SquareInSquareMaker.class),
//    LETTUCE(LettuceMaker.class),
//    GROWTH_PATTERN(GrowthMaker.class),
    DIAGONAL_LINES(DiagonalMaker.class);

    final Class<? extends Maker> maker;

    ChaosType(Class<? extends Maker> maker) {
        this.maker = maker;
    }
}
