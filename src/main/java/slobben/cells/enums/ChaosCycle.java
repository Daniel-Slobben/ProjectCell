package slobben.cells.enums;

import java.util.Map;
import java.util.Random;

import static slobben.cells.enums.ChaosType.*;

public enum ChaosCycle {
    MIXED(30, Map.of(
            SQUARE, new SizeRange(2000, 20000),
            LETTUCE, new SizeRange(2000, 20000),
            GROWTH_PATTERN, new SizeRange(2000, 20000),
            SPACESHIP, new SizeRange(2000, 20000),
            DIAGONAL_LINES, new SizeRange(2000, 20000))),
    SMALL_SWARM(60, Map.of(
            SQUARE, new SizeRange(1000, 5000),
            LETTUCE, new SizeRange(1000, 5000),
            GROWTH_PATTERN, new SizeRange(1000, 5000),
            SPACESHIP, new SizeRange(1000, 5000),
            DIAGONAL_LINES, new SizeRange(1000, 5000))),
    GIANTS(5, Map.of(
            SQUARE, new SizeRange(10000, 30000),
            LETTUCE, new SizeRange(10000, 30000),
            DIAGONAL_LINES, new SizeRange(10000, 30000))),
    FLEET(30, Map.of(
            SPACESHIP, new SizeRange(4000, 20000),
            GROWTH_PATTERN, new SizeRange(2000, 6000)));

    private static final SizeRange DEFAULT_SIZE = new SizeRange(2000, 20000);

    public final int hitsAtStart;
    private final Map<ChaosType, SizeRange> sizes;
    private final int totalWeight;

    ChaosCycle(int hitsAtStart, Map<ChaosType, SizeRange> sizes) {
        this.hitsAtStart = hitsAtStart;
        this.sizes = sizes;
        this.totalWeight = sizes.keySet().stream().mapToInt(type -> type.weight).sum();
    }

    public static ChaosCycle getRandomCycle(Random random) {
        return values()[random.nextInt(values().length)];
    }

    public ChaosType getRandomType(Random random) {
        int roll = random.nextInt(totalWeight);
        for (ChaosType type : ChaosType.values()) {
            if (sizes.containsKey(type)) {
                roll -= type.weight;
                if (roll < 0) {
                    return type;
                }
            }
        }
        throw new IllegalStateException("Roll exceeded total weight " + totalWeight);
    }

    // skewed towards the small end
    public int getRandomSize(ChaosType type, Random random) {
        SizeRange range = sizes.getOrDefault(type, DEFAULT_SIZE);
        double skewed = Math.pow(random.nextDouble(), 3.0);
        return range.min() + (int) (skewed * (range.max() - range.min()));
    }

    public record SizeRange(int min, int max) {
    }
}
