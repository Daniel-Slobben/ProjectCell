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
            ChaosType type = ChaosCycle.FLEET.getRandomType(random);
            int size = ChaosCycle.FLEET.getRandomSize(type, random);
            assertThat(type).isIn(ChaosType.SPACESHIP, ChaosType.GROWTH_PATTERN);
            assertThat(size).isBetween(type == ChaosType.SPACESHIP ? 4000 : 2000, type == ChaosType.SPACESHIP ? 20000 : 6000);
        }
    }

    @Test
    void aTypeOutsideTheCycleGetsTheDefaultSize() {
        // execute
        int size = ChaosCycle.FLEET.getRandomSize(ChaosType.SQUARE, new Random(42));

        // verify
        assertThat(size).isBetween(2000, 20000);
    }
}
