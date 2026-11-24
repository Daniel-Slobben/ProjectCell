package slobben.cells.enums;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class ChaosCycleTest {

    @Test
    void picksOnlyTypesOfTheCycleWithinTheirSizes() {
        // prepare
        Random random = new Random(42);

        // execute and verify
        for (int i = 0; i < 1000; i++) {
            ChaosType type = ChaosCycle.GIANTS.getRandomType(random);
            int size = ChaosCycle.GIANTS.getRandomSize(type, random);
            assertThat(type).isIn(ChaosType.SQUARE, ChaosType.SPACESHIP, ChaosType.DIAGONAL_LINES);
            assertThat(size).isBetween(type == ChaosType.SPACESHIP ? 1000 : 10000, type == ChaosType.SPACESHIP ? 3000 : 30000);
        }
    }

    @Test
    void picksTypesByTheirWeight() {
        // prepare
        Random random = new Random(42);
        int spaceships = 0;

        // execute
        for (int i = 0; i < 14000; i++) {
            if (ChaosCycle.GIANTS.getRandomType(random) == ChaosType.SPACESHIP) {
                spaceships++;
            }
        }

        // verify
        assertThat(spaceships).isBetween(7500, 8500);
    }

    @Test
    void aTypeOutsideTheCycleGetsTheDefaultSize() {
        // execute
        int size = ChaosCycle.GIANTS.getRandomSize(ChaosType.LETTUCE, new Random(42));

        // verify
        assertThat(size).isBetween(2000, 20000);
    }
}
