package slobben.cells.enums;

import java.util.Map;
import java.util.Random;

import static slobben.cells.enums.ChaosType.*;

public enum ChaosCycle {
    MIXED(30, Map.of(
            SQUARE, new Hit(3, 2000, 20000),
            LETTUCE, new Hit(3, 2000, 20000),
            GROWTH_PATTERN, new Hit(8, 2000, 20000),
            DIAGONAL_LETTUCE, new Hit(3, 2000, 20000),
            SPACESHIP, new Hit(8, 2000, 20000),
            DIAGONAL_LINES, new Hit(3, 2000, 20000))),
    SMALL_SWARM(60, Map.of(
            SQUARE, new Hit(3, 1000, 5000),
            DIAGONAL_LETTUCE, new Hit(3, 2000, 5000),
            LETTUCE, new Hit(3, 1000, 5000),
            GROWTH_PATTERN, new Hit(8, 1000, 5000),
            SPACESHIP, new Hit(8, 1000, 5000),
            DIAGONAL_LINES, new Hit(3, 1000, 5000))),
    GIANTS(5, Map.of(
            SQUARE, new Hit(3, 10000, 30000),
            SPACESHIP, new Hit(8, 1000, 3000),
            DIAGONAL_LINES, new Hit(3, 10000, 30000)));

    private static final Hit DEFAULT_HIT = new Hit(1, 2000, 20000);

    public final int hitsAtStart;
    private final Map<ChaosType, Hit> hits;
    private final int totalWeight;

    ChaosCycle(int hitsAtStart, Map<ChaosType, Hit> hits) {
        this.hitsAtStart = hitsAtStart;
        this.hits = hits;
        this.totalWeight = hits.values().stream().mapToInt(Hit::weight).sum();
    }

    public static ChaosCycle getRandomCycle(Random random) {
        return values()[random.nextInt(values().length)];
    }

    public ChaosType getRandomType(Random random) {
        int roll = random.nextInt(totalWeight);
        for (ChaosType type : ChaosType.values()) {
            if (hits.containsKey(type)) {
                roll -= hits.get(type).weight();
                if (roll < 0) {
                    return type;
                }
            }
        }
        throw new IllegalStateException("Roll exceeded total weight " + totalWeight);
    }

    // skewed towards the small end
    public int getRandomSize(ChaosType type, Random random) {
        Hit range = hits.getOrDefault(type, DEFAULT_HIT);
        double skewed = Math.pow(random.nextDouble(), 2.0);
        return range.min() + (int) (skewed * (range.max() - range.min()));
    }

    public record Hit(int weight, int min, int max) {
    }
}
