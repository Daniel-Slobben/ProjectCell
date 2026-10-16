package slobben.cells.enums;

import slobben.cells.service.workers.chaosmakers.DiagonalMaker;
import slobben.cells.service.workers.chaosmakers.GrowthMaker;
import slobben.cells.service.workers.chaosmakers.Maker;
import slobben.cells.service.workers.chaosmakers.SquareMaker;

public enum ChaosType {
    SQUARE(SquareMaker.class),
    //    SQUARE_IN_SQUARE(SquareInSquareMaker.class),
//    LETTUCE(LettuceMaker.class),
    GROWTH_PATTERN(GrowthMaker.class),
    DIAGONAL_LINES(DiagonalMaker.class);

    public final Class<? extends Maker> maker;

    ChaosType(Class<? extends Maker> maker) {
        this.maker = maker;
    }
}
