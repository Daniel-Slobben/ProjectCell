package slobben.cells.service.workers.chaos;

import slobben.cells.service.workers.chaos.makers.*;

enum ChaosType {
    SQUARE {
        @Override
        Maker getMaker() {
            return new SquareMaker();
        }
    },
    SQUARE_IN_SQUARE {
        @Override
        Maker getMaker() {
            return new SquareInSquareMaker();
        }
    },
    LINE_MAKER {
        @Override
        Maker getMaker() {
            return new LineMaker();
        }
    },
    GROWTH_PATTERN {
        @Override
        Maker getMaker() {
            return new GrowthMaker();
        }
    },
    OSCILLATORS {
        @Override
        Maker getMaker() {
            return new OscillatorMaker();
        }
    };

    abstract Maker getMaker();
}
