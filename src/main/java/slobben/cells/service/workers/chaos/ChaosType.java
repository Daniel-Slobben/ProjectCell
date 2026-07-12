package slobben.cells.service.workers.chaos;

import slobben.cells.service.workers.chaos.makers.LineMaker;
import slobben.cells.service.workers.chaos.makers.Maker;
import slobben.cells.service.workers.chaos.makers.SquareInSquareMaker;
import slobben.cells.service.workers.chaos.makers.SquareMaker;

enum ChaosType {
    SQUARE(new SquareMaker()),
    SQUARE_IN_SQUARE(new SquareInSquareMaker()),
    LINE_MAKER(new LineMaker());
//    GROWTH_PATTERN(new GrowthMaker()),
//    OSCILLATORS(new OscillatorMaker());

    final Maker maker;

    ChaosType(Maker maker) {
        this.maker = maker;
    }
}
