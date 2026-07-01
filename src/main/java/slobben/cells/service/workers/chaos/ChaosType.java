package slobben.cells.service.workers.chaos;

import slobben.cells.service.workers.chaos.makers.*;

enum ChaosType {
    SQUARE(new SquareMaker()),
    SQUARE_IN_SQUARE(new SquareInSquareMaker()),
    LINE_MAKER(new LineMaker()),
    GROWTH_PATTERN(new GrowthMaker()),
    OSCILLATORS(new OscillatorMaker());

    final Maker maker;

    ChaosType(Maker maker) {
        this.maker = maker;
    }
}
