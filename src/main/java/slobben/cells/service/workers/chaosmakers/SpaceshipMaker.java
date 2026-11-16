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
    private static final int MIN_SHIPS = 3;
    private static final int MAX_SHIPS = 10;
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

        boolean isDiagonal() {
            return dx != 0 && dy != 0;
        }
    }

    private enum FleetKind {SAME_SHIP, MIXED, ONE_DIRECTION}

    private record Ship(String pattern, int rotation) {
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
        int count = random.nextInt(MIN_SHIPS, MAX_SHIPS + 1);
        FleetKind kind = FleetKind.values()[random.nextInt(FleetKind.values().length)];

        List<Ship> fleet = new ArrayList<>();
        String label;
        switch (kind) {
            case SAME_SHIP -> {
                fleet.addAll(Collections.nCopies(count, new Ship(randomPattern(patterns), random.nextInt(4))));
                label = "same ship";
            }
            case MIXED -> {
                for (int i = 0; i < count; i++) {
                    fleet.add(new Ship(randomPattern(patterns), random.nextInt(4)));
                }
                label = "mixed";
            }
            case ONE_DIRECTION -> {
                Direction heading = Direction.values()[random.nextInt(8)];
                List<String> candidates = patterns.stream().filter(p -> directions.get(p).isDiagonal() == heading.isDiagonal()).toList();
                for (int i = 0; i < count; i++) {
                    String pattern = randomPattern(candidates);
                    fleet.add(new Ship(pattern, Math.floorMod(directions.get(pattern).ordinal() - heading.ordinal(), 8) / 2));
                }
                label = "heading " + heading;
            }
            default -> throw new IllegalStateException("Unknown fleet kind " + kind);
        }

        // ships are written side by side, each in its own square, so they never overlap
        int offset = 0;
        int width = 0;
        int tallest = 0;
        for (Ship ship : fleet) {
            try {
                int shipSize = rleReader.writePatternToWorld(DIR + ship.pattern(), new Coordinates(worldX + offset, worldY), chaosHitId, (byte) ship.rotation());
                width = offset + shipSize;
                offset = width + random.nextInt(MIN_GAP, MAX_GAP + 1);
                tallest = Math.max(tallest, shipSize);
            } catch (IOException e) {
                log.error("Error reading .rle. continue.", e);
            }
        }

        // the fleet is written from its corner, the hit is tracked from its center
        Coordinates center = new Coordinates(worldX + width / 2, worldY + tallest / 2);
        String names = String.join(", ", fleet.stream().map(Ship::pattern).toList());
        return new ChaosHit("Spaceships, %s: %s".formatted(label, names), chaosHitId, center, 100);
    }

    private String randomPattern(List<String> from) {
        return from.get(random.nextInt(from.size()));
    }
}
