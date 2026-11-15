package slobben.cells.service.workers.chaosmakers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import slobben.cells.dto.internal.Coordinates;
import slobben.cells.entities.ChaosHit;
import slobben.cells.service.RleReader;

import java.io.IOException;
import java.util.*;

@Slf4j
@Component
public class SpaceshipMaker implements Maker {
    private static final String DIR = "spaceships/";
    private static final int MIN_SHIPS = 3;
    private static final int MAX_SHIPS = 10;
    private static final int MIN_GAP = 30;
    private static final int MAX_GAP = 100;

    private final Random random = new Random();
    private final RleReader rleReader;
    private final List<String> patterns;

    public SpaceshipMaker(RleReader rleReader) throws IOException {
        this.rleReader = rleReader;
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources("classpath:patterns/" + DIR + "*.rle");
        this.patterns = Arrays.stream(resources)
                .map(Resource::getFilename).filter(Objects::nonNull)
                .map(name -> name.replace(".rle", ""))
                .toList();
        if (patterns.isEmpty()) {
            throw new IllegalStateException("No spaceship patterns found in patterns/" + DIR);
        }
        log.info("Loaded {} spaceship patterns", patterns.size());
    }

    @Override
    public ChaosHit getChaosHit(int worldX, int worldY, int size) {
        UUID chaosHitId = UUID.randomUUID();
        List<String> fleet = new ArrayList<>();

        // ships are written side by side, each in its own square, so they never overlap
        int offset = 0;
        int tallest = 0;
        for (int i = random.nextInt(MIN_SHIPS, MAX_SHIPS + 1); i > 0; i--) {
            String pattern = patterns.get(random.nextInt(patterns.size()));
            try {
                int shipSize = rleReader.writePatternToWorld(DIR + pattern, new Coordinates(worldX + offset, worldY), chaosHitId, (byte) random.nextInt(0, 4));
                offset += shipSize + random.nextInt(MIN_GAP, MAX_GAP + 1);
                tallest = Math.max(tallest, shipSize);
                fleet.add(pattern);
            } catch (IOException e) {
                log.error("Error reading .rle. continue.", e);
            }
        }

        // the fleet is written from its corner, the hit is tracked from its center
        Coordinates center = new Coordinates(worldX + offset / 2, worldY + tallest / 2);
        return new ChaosHit("Spaceships " + String.join(", ", fleet), chaosHitId, center, 100);
    }
}
