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
        String pattern = patterns.get(random.nextInt(patterns.size()));

        int shipSize = 0;
        try {
            shipSize = rleReader.writePatternToWorld(DIR + pattern, new Coordinates(worldX, worldY), chaosHitId, (byte) random.nextInt(0, 4));
        } catch (IOException e) {
            log.error("Error reading .rle. continue.", e);
        }

        // the pattern is written from its corner, the hit is tracked from its center
        Coordinates center = new Coordinates(worldX + shipSize / 2, worldY + shipSize / 2);
        return new ChaosHit("Spaceship " + pattern, chaosHitId, center, 100, shipSize);
    }
}
