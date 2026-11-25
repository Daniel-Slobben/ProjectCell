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
            ChaosType type = ChaosCycle.BIG_SQUARE.getRandomType(random);
            int size = ChaosCycle.BIG_SQUARE.getRandomSize(type, random);
            assertThat(type).isEqualTo(ChaosType.SQUARE);
            assertThat(size).isBetween(60000, 70000);
        }
    }

    @Test
    void picksTypesByTheirWeight() {
        // prepare
        Random random = new Random(42);
        int spaceships = 0;

        // execute
        for (int i = 0; i < 28000; i++) {
            if (ChaosCycle.MIXED.getRandomType(random) == ChaosType.SPACESHIP) {
                spaceships++;
            }
        }

        // verify
        assertThat(spaceships).isBetween(7500, 8500);
    }
}
