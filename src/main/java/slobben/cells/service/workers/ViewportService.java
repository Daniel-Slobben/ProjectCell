package slobben.cells.service.workers;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import slobben.cells.dto.internal.Coordinates;
import slobben.cells.entities.Block;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ViewportService extends Worker {

    @Value("${cells.size.blockSize}")
    private int blockSize = 500;
    @Value("${cells.size.sectorSize}")
    private int sectorSize = 40;

    private final Map<String, Block> blocks;
    private List<CoordinateEntry> interestingCoordinates = new ArrayList<>();

    private static final int COORDINATES_MAX = 20;
    private static final int RECENT_DISTANCE_BLOCKS = 3;
    private static final Random RANDOM = new Random();

    record CoordinateEntry(short count, Coordinates coordinates) {
    }

    @Override
    public void execute() {
        short lowestHighCount = 0;
        List<CoordinateEntry> newCoordinates = new ArrayList<>();

        for (Block block : blocks.values()) {
            CoordinateEntry best = bestSector(block);

            if (best != null && (newCoordinates.size() < COORDINATES_MAX || best.count() > lowestHighCount)) {
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

    // a block only offers its best sector, so one busy block can't fill the whole list
    private CoordinateEntry bestSector(Block block) {
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

                    best = new CoordinateEntry(count, new Coordinates(worldX, worldY));
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
