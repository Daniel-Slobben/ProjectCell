package slobben.cells.service.workers.chaos.makers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import slobben.cells.dto.internal.Coordinates;
import slobben.cells.service.RleReader;
import slobben.cells.service.workers.chaos.ChaosHit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;


@Slf4j
@RequiredArgsConstructor
@Service
public class GrowthMaker implements Maker {
    private static final int MIN_SIZE = 1000;
    private static final int MAX_SIZE = 10000;
    private static final int MIN_POPULATION = 40;
    private static final int MAX_POPULATION = 100;

    private final Random random = new Random();
    private final String[] growthPatterns = {"spacefiller1.rle", "spacefiller2.rle",};
    private final String[] otherPatterns = {"tlogtgrowth.rle", "greyship"};
    private final RleReader rleReader;

    @Override
    public ChaosHit getChaosHit(int worldX, int worldY) {
        UUID id = UUID.randomUUID();
        int population = random.nextInt(MIN_POPULATION, MAX_POPULATION + 1);

        List<Coordinates> growthHits = new ArrayList<>();
        for (int p = 0; p < population; p++) {
            int x = getRandomNumberWithNegative();
            int y = getRandomNumberWithNegative();
            try {
                Coordinates coordinates = new Coordinates(x + worldX, y + worldY);
                String patternToAdd;
                if (random.nextBoolean()) {
                    growthHits.add(coordinates);
                    patternToAdd = growthPatterns[random.nextInt(0, growthPatterns.length)];
                } else {
                    patternToAdd = otherPatterns[random.nextInt(0, otherPatterns.length)];
                }
                rleReader.writePatternToWorld(patternToAdd, coordinates, id, (byte) random.nextInt(0, 3));
            } catch (IOException e) {
                log.error("Error reading .rle. continue.", e);
            }
        }

        return new ChaosHit("", ((int i) -> {
            Coordinates coordinates = growthHits.get(random.nextInt(0, growthHits.size()));
            return new Coordinates(coordinates.x() + i / 2, coordinates.y() + i / 2);
        }), 10_000);
    }

    private int getRandomNumberWithNegative() {
        int x = random.nextInt(MIN_SIZE, MAX_SIZE);
        if (random.nextBoolean()) {
            x = Math.negateExact(x);
        }
        return x;
    }

}
