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
    private static final int MIN_SIZE = 15000;
    private static final int MAX_SIZE = 30000;
    private static final int MIN_POPULATION = 200;
    private static final int MAX_POPULATION = 500;

    private final Random random = new Random();
    private final String[] growthPatterns = {"spacefiller1.rle", "spacefiller2.rle",};
    private final String[] otherPatterns = {"greyship"};
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

        return new ChaosHit("", id, growthHits.getFirst(), 10_000);
    }

    private int getRandomNumberWithNegative() {
        int x = random.nextInt(MIN_SIZE, MAX_SIZE);
        if (random.nextBoolean()) {
            x = Math.negateExact(x);
        }
        return x;
    }

}
