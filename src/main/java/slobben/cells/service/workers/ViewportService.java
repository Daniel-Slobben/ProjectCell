package slobben.cells.service.workers;

import lombok.RequiredArgsConstructor;
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
    private static final Random RANDOM = new Random();

    record CoordinateEntry(short count, Coordinates coordinates, boolean growth) {
    }

    @Override
    public void execute() {
        Set<UUID> growthHits = chaosHits.stream()
                .filter(hit -> hit.getType() == ChaosType.GROWTH_PATTERN)
                .map(ChaosHit::getId)
                .collect(Collectors.toSet());
        List<CoordinateEntry> busiestFirst = blocks.values().stream()
                .map(block -> bestSector(block, growthHits.contains(block.getResponsibleChaosHit())))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(CoordinateEntry::count).reversed())
                .toList();

        List<CoordinateEntry> newCoordinates = new ArrayList<>();
        int growthCount = 0;
        for (CoordinateEntry entry : busiestFirst) {
            if (newCoordinates.size() == COORDINATES_MAX) {
                break;
            }
            if (entry.growth() && growthCount++ >= GROWTH_MAX) {
                continue;
            }
            newCoordinates.add(entry);
        }
        interestingCoordinates = newCoordinates;
    }

    // a block only offers its best sector, so one busy block can't fill the whole list
    private CoordinateEntry bestSector(Block block, boolean growth) {
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

                    best = new CoordinateEntry(count, new Coordinates(worldX, worldY), growth);
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
        List<CoordinateEntry> farFromRecent = viewportListSnapshot.stream()
                .filter(entry -> recentViewports.stream().noneMatch(recent -> isNear(entry.coordinates(), recent)))
                .toList();
        return pickRandom(farFromRecent.isEmpty() ? viewportListSnapshot : farFromRecent);
    }

    private Coordinates pickRandom(List<CoordinateEntry> entries) {
        return entries.get(RANDOM.nextInt(0, entries.size())).coordinates();
    }

    private boolean isNear(Coordinates a, Coordinates b) {
        int distance = RECENT_DISTANCE_BLOCKS * blockSize;
        return Math.abs(a.x() - b.x()) <= distance && Math.abs(a.y() - b.y()) <= distance;
    }
}
