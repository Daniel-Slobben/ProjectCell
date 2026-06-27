package slobben.cells.service.workers.chaos.makers;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.util.Pair;
import slobben.cells.entities.Pattern;
import slobben.cells.service.workers.chaos.ChaosHit;
import slobben.cells.util.RleReader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static slobben.cells.util.RleReader.PatternCategories.GROWTH_PATTERNS;
import static slobben.cells.util.RleReader.PatternCategories.OSCILLATORS;

@Slf4j
public class GrowthMaker implements Maker {
    private static final int MIN_SIZE = 500;
    private static final int MAX_SIZE = 1500;
    private static final int MIN_POPULATION = 4;
    private static final int MAX_POPULATION = 12;

    private final RleReader rleReader = new RleReader();
    private final Random random = new Random();
    private final List<Pattern> allowedGrowthPatterns = new ArrayList<>();

    @SneakyThrows
    public GrowthMaker() {
        allowedGrowthPatterns.add(rleReader.readPatternFromFilename(GROWTH_PATTERNS.directory + "/spacefiller1"));
        allowedGrowthPatterns.add(rleReader.readPatternFromFilename(GROWTH_PATTERNS.directory + "/spacefiller2"));
    }

    @Override
    public ChaosHit getChaosHit(int worldTargetX, int worldTargetY) {
        int size = random.nextInt(MIN_SIZE, MAX_SIZE + 1);
        int population = random.nextInt(MIN_POPULATION, MAX_POPULATION + 1);
        boolean[][] matrix = new boolean[size][size];

        Pattern growthPattern = getRandomFiller();
        // add first filler always
        final Pair<Integer, Integer> fillerOffsetPair = getRandomOffsetPair(growthPattern, matrix);
        addPatternToMatrix(growthPattern, matrix, fillerOffsetPair.getFirst(), fillerOffsetPair.getSecond());

        // then fillup with leftover
        for (int p = 1; p < population; p++) {
            Pattern pattern;
            // either another filler or random
            if (random.nextBoolean()) {
                pattern = getRandomFiller();
            } else {
                try {
                    pattern = rleReader.readRandomPatternFromCategoryWithSize(OSCILLATORS.name(), size / 3);
                } catch (IOException e) {
                    log.error(e.getMessage());
                    population++;
                    continue;
                }
            }
            var offsetPair = getRandomOffsetPair(pattern, matrix);
            addPatternToMatrix(pattern, matrix, offsetPair.getFirst(), offsetPair.getSecond());
        }

        Pattern pattern = Pattern.builder()
                .x(matrix.length)
                .y(matrix[0].length)
                .matrix(matrix)
                .build();
        return new ChaosHit(worldTargetX + fillerOffsetPair.getFirst(), worldTargetY + fillerOffsetPair.getSecond(),
                "Growthpattern with oscillators for collapse", pattern);
    }

    private Pattern getRandomFiller() {
        return allowedGrowthPatterns.get(random.nextInt(0, allowedGrowthPatterns.size()));
    }

    private Pair<Integer, Integer> getRandomOffsetPair(Pattern pattern, boolean[][] matrix) {
        int xOffset = random.nextInt(0, matrix.length - pattern.x());
        int yOffset = random.nextInt(0, matrix[0].length - pattern.y());

        return Pair.of(xOffset, yOffset);
    }

}
