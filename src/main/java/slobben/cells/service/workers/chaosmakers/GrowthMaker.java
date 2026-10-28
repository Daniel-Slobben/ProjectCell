package slobben.cells.service.workers.chaosmakers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import slobben.cells.dto.internal.Coordinates;
import slobben.cells.entities.ChaosHit;
import slobben.cells.service.RleReader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;


@Slf4j
@RequiredArgsConstructor
@Service
public class GrowthMaker implements Maker {
    private static final int MIN_POPULATION = 200;
    private static final int MAX_POPULATION = 500;

    private final Random random = new Random();
    private final String[] growthPatterns = {"spacefiller1.rle", "spacefiller2.rle",};
    private final String[] otherPatterns = {"greyship"};
    private final RleReader rleReader;

    @Override
    public ChaosHit getChaosHit(int worldX, int worldY, int size) {
        UUID id = UUID.randomUUID();
        int population = random.nextInt(MIN_POPULATION, MAX_POPULATION + 1);

        List<Coordinates> growthHits = new ArrayList<>();
        for (int p = 0; p < population; p++) {
            Coordinates relativeCoordinates = getRandomCoordinatesWithinBound(size);

            try {
                Coordinates coordinates = new Coordinates(relativeCoordinates.x() + worldX, relativeCoordinates.y() + worldY);
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

        return new ChaosHit("", id, new Coordinates(worldX, worldY), 10_000, size);
    }

    private Coordinates getRandomCoordinatesWithinBound(int bound) {
        final int halfBound = bound / 2;
        int x = random.nextInt(-halfBound, halfBound);
        int y = random.nextInt(-halfBound, halfBound);
        return new Coordinates(x, y);
    }

}
