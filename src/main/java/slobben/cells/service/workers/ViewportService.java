package slobben.cells.service.workers;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import slobben.cells.dto.internal.Coordinates;
import slobben.cells.entities.Block;
import slobben.cells.entities.ChaosHit;
import slobben.cells.enums.ChaosType;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ViewportService extends Worker {

    @Value("${cells.size.blockSize}")
    private int blockSize = 500;
    @Value("${cells.size.sectorSize}")
    private int sectorSize = 40;

    private final Map<String, Block> blocks;
    private final List<ChaosHit> chaosHits;
    private List<CoordinateEntry> interestingCoordinates = new ArrayList<>();

    private static final int COORDINATES_MAX = 50;
    // growth patterns fill many busy blocks that all look alike
    private static final int GROWTH_MAX = 3;
    private static final int RECENT_DISTANCE_BLOCKS = 3;
    // the frontend sends its last 5
    private static final int RECENT_MAX = 10;
    private static final Random RANDOM = new Random();

    record CoordinateEntry(short count, Coordinates coordinates, boolean isGrowthHit) {
    }

    @Override
    public void execute() {
        Set<UUID> growthHits = chaosHits.stream()
                .filter(hit -> hit.getType() == ChaosType.GROWTH_PATTERN)
                .map(ChaosHit::getId)
                .collect(Collectors.toSet());
        short lowestHighCount = 0;
        List<CoordinateEntry> newCoordinates = new ArrayList<>();

        for (Block block : blocks.values()) {
            CoordinateEntry best = bestSector(block, growthHits.contains(block.getResponsibleChaosHit()));
            if (best == null) {
                continue;
            }

            boolean growthFull = best.isGrowthHit()
                    && newCoordinates.stream().filter(CoordinateEntry::isGrowthHit).count() >= GROWTH_MAX;

            if (!growthFull && (newCoordinates.size() < COORDINATES_MAX || best.count() > lowestHighCount)) {
                if (newCoordinates.size() >= COORDINATES_MAX) {
                    newCoordinates.removeLast();
                }

                newCoordinates.add(best);
                newCoordinates.sort(Comparator.comparing(CoordinateEntry::count).reversed());
                lowestHighCount = newCoordinates.getLast().count();
            }
        }
        interestingCoordinates = newCoordinates;
    }

    private @Nullable CoordinateEntry bestSector(Block block, boolean isGrowthHit) {
        short[][] sectorCount = block.getSectorCounts();
        CoordinateEntry best = null;

        for (int sx = 0; sx < sectorCount.length; sx++) {
            for (int sy = 0; sy < sectorCount.length; sy++) {
                short count = sectorCount[sx][sy];

                if ((best == null || count > best.count())
                        // filters for a minimum threshold
                        && count > sectorSize + sectorSize
                        // filters sectors with just pure lines
                        && count % sectorSize != 0) {

                    int worldX = block.getX() * blockSize + sx * sectorSize;
                    int worldY = block.getY() * blockSize + sy * sectorSize;

                    best = new CoordinateEntry(count, new Coordinates(worldX, worldY), isGrowthHit);
                }
            }
        }
        return best;
    }

    @Override
    public String getName() {
        return "Viewport Service";
    }

    public Coordinates getInterestingViewport(List<Coordinates> recentViewports) {
        List<CoordinateEntry> viewportListSnapshot = interestingCoordinates;
        if (viewportListSnapshot.isEmpty()) {
            return new Coordinates(0, 0);
        }
        List<Coordinates> recent = recentViewports.subList(Math.max(0, recentViewports.size() - RECENT_MAX), recentViewports.size());
        List<CoordinateEntry> farFromRecent = viewportListSnapshot.stream()
                .filter(entry -> recent.stream().noneMatch(viewport -> viewport != null && isNear(entry.coordinates(), viewport)))
                .toList();

        List<CoordinateEntry> candidates = farFromRecent.isEmpty()
                ? viewportListSnapshot
                : farFromRecent;
        return candidates.get(RANDOM.nextInt(0, candidates.size()))
                .coordinates();
    }

    private boolean isNear(Coordinates a, Coordinates b) {
        int distance = RECENT_DISTANCE_BLOCKS * blockSize;
        return Math.abs(a.x() - b.x()) <= distance && Math.abs(a.y() - b.y()) <= distance;
    }
}
