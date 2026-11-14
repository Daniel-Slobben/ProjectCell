package slobben.cells.service.workers.chaosmakers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import slobben.cells.dto.internal.Coordinates;
import slobben.cells.entities.ChaosHit;
import slobben.cells.service.RleReader;

import java.io.IOException;
import java.util.Random;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class GrowthMaker implements Maker {
    private static final String[] PATTERNS = {"spacefiller1", "spacefiller2"};

    private final Random random = new Random();
    private final RleReader rleReader;

    @Override
    public ChaosHit getChaosHit(int worldX, int worldY, int size) {
        UUID chaosHitId = UUID.randomUUID();
        String pattern = PATTERNS[random.nextInt(PATTERNS.length)];

        try {
            rleReader.writePatternToWorld(pattern, new Coordinates(worldX, worldY), chaosHitId, (byte) random.nextInt(0, 3));
        } catch (IOException e) {
            log.error("Error reading .rle. continue.", e);
        }

        // a growth pattern keeps growing, so it doesn't hold a cycle open
        return new ChaosHit("Growth pattern " + pattern, chaosHitId, new Coordinates(worldX, worldY), 100, size);
    }
}
