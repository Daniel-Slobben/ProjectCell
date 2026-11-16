package slobben.cells.service.workers.chaosmakers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import slobben.cells.dto.internal.Coordinates;
import slobben.cells.entities.ChaosHit;
import slobben.cells.service.RleReader;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.*;

@Slf4j
@Component
public class SpaceshipMaker implements Maker {
    public static final String DIR = "spaceships/";
    private static final int MIN_GAP = 30;
    private static final int MAX_GAP = 100;

    private final Random random = new Random();
    private final RleReader rleReader;
    private final Map<String, Direction> directions;
    private final List<String> patterns;

    // where a ship heads on screen when written at rotation 0; every rotation turns it 90° counterclockwise
    public enum Direction {
        N(0, -1), NE(1, -1), E(1, 0), SE(1, 1), S(0, 1), SW(-1, 1), W(-1, 0), NW(-1, -1);

        public final int dx;
        public final int dy;

        Direction(int dx, int dy) {
            this.dx = dx;
            this.dy = dy;
        }

        public Direction rotated(int rotation) {
            return values()[Math.floorMod(ordinal() - 2 * rotation, 8)];
        }
    }

    public SpaceshipMaker(RleReader rleReader) {
        this.rleReader = rleReader;
        this.directions = readDirections();
        this.patterns = List.copyOf(directions.keySet());
        log.info("Loaded {} spaceship patterns", patterns.size());
    }

    public static Map<String, Direction> readDirections() {
        try (InputStream in = new ClassPathResource("patterns/" + DIR + "directions.json").getInputStream()) {
            return new ObjectMapper().readValue(in, new TypeReference<Map<String, Direction>>() {
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public ChaosHit getChaosHit(int worldX, int worldY, int size) {
        UUID chaosHitId = UUID.randomUUID();
        String pattern = patterns.get(random.nextInt(patterns.size()));
        byte rotation = (byte) random.nextInt(4);
        Direction heading = directions.get(pattern).rotated(rotation);

        // the leader goes in the middle of the hit, the rest fall in behind it in a V, as far as the hit's size allows
        int shipCount = 0;
        try {
            int slot = write(pattern, worldX, worldY, chaosHitId, rotation) + random.nextInt(MIN_GAP, MAX_GAP + 1);
            shipCount++;
            int reach = size / 2 - slot;
            for (int rank = 1; ; rank++) {
                List<Coordinates> spots = vSpots(heading, rank);
                if (spots.stream().anyMatch(spot -> Math.abs(spot.x()) * slot > reach || Math.abs(spot.y()) * slot > reach)) {
                    break;
                }
                for (Coordinates spot : spots) {
                    write(pattern, worldX + spot.x() * slot, worldY + spot.y() * slot, chaosHitId, rotation);
                    shipCount++;
                }
            }
        } catch (IOException e) {
            log.error("Error reading .rle. continue.", e);
        }

        String name = "Spaceships, %s %s heading %s".formatted(shipCount, pattern, heading);
        return new ChaosHit(name, chaosHitId, new Coordinates(worldX, worldY), 100);
    }

    private int write(String pattern, int x, int y, UUID chaosHitId, byte rotation) throws IOException {
        return rleReader.writePatternToWorld(DIR + pattern, new Coordinates(x, y), chaosHitId, rotation);
    }

    // one rank behind the leader per rank, out to both sides like birds
    private static List<Coordinates> vSpots(Direction heading, int rank) {
        int backX = -heading.dx * rank;
        int backY = -heading.dy * rank;
        return List.of(new Coordinates(backX - heading.dy * rank, backY + heading.dx * rank), new Coordinates(backX + heading.dy * rank, backY - heading.dx * rank));
    }
}
