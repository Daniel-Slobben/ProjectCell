package slobben.cells.service.workers;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import slobben.cells.dto.internal.Coordinates;
import slobben.cells.entities.Block;
import slobben.cells.entities.ChaosHit;
import slobben.cells.enums.ChaosType;
import slobben.cells.util.BlockUtils;

import java.util.*;
import java.util.function.Predicate;
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
    private Map<String, UUID> blockChaosHits = Map.of();
    private Map<UUID, ChaosType> chaosHitTypes = Map.of();

    private static final int COORDINATES_MAX = 50;
    private static final int RECENT_DISTANCE_BLOCKS = 3;
    private static final int RECENT_CHAOS_TYPES = 3;
    private static final Random RANDOM = new Random();

    record CoordinateEntry(short count, Coordinates coordinates, UUID chaosHit) {
    }

    @Override
    public void execute() {
        short lowestHighCount = 0;
        List<CoordinateEntry> newCoordinates = new ArrayList<>();
        Map<String, UUID> newBlockChaosHits = new HashMap<>();

        for (Block block : blocks.values()) {
            newBlockChaosHits.put(block.getKey(), block.getResponsibleChaosHit());
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
        blockChaosHits = newBlockChaosHits;
        chaosHitTypes = chaosHits.stream()
                .filter(hit -> hit.getType() != null)
                .collect(Collectors.toMap(ChaosHit::getId, ChaosHit::getType));
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
                        && count % sectorSize != 0
                        // filters the zebra stripes an intact spacefiller leaves behind
                        && 2 * stripeCells(block.getPackedCells(), sx, sy) < count) {

                    int worldX = block.getX() * blockSize + sx * sectorSize;
                    int worldY = block.getY() * blockSize + sy * sectorSize;

                    best = new CoordinateEntry(count, new Coordinates(worldX, worldY), block.getResponsibleChaosHit());
                }
            }
        }
        return best;
    }

    // cells with both neighbours along their row or column and none beside it
    private int stripeCells(long[][] cells, int sx, int sy) {
        int from = sy * sectorSize + 1;
        int to = from + sectorSize - 1;
        int stripes = 0;
        for (int x = sx * sectorSize + 1; x <= sx * sectorSize + sectorSize; x++) {
            long[] above = cells[x - 1];
            long[] row = cells[x];
            long[] below = cells[x + 1];
            for (int w = (from >>> 6) + 1; w <= (to >>> 6) + 1; w++) {
                long low = row[w] << 1 | row[w - 1] >>> 63;
                long high = row[w] >>> 1 | row[w + 1] << 63;
                long along = low & high & ~above[w] & ~below[w];
                long across = above[w] & below[w] & ~low & ~high;
                stripes += Long.bitCount(row[w] & (along | across) & columnMask(w, from, to));
            }
        }
        return stripes;
    }

    // the bits of columns from .. to in word w of a packed row
    private static long columnMask(int w, int from, int to) {
        int base = (w - 1) << 6;
        return -1L >>> (63 - Math.min(to - base, 63)) & -1L << Math.max(from - base, 0);
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
        Map<UUID, ChaosType> typeSnapshot = chaosHitTypes;
        List<UUID> recentHits = recentChaosHits(recentViewports);
        Set<ChaosType> recentTypes = recentHits.subList(Math.max(0, recentHits.size() - RECENT_CHAOS_TYPES), recentHits.size())
                .stream()
                .map(typeSnapshot::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // most important first; the last one is dropped until something matches
        List<Predicate<CoordinateEntry>> preferences = List.of(
                entry -> !recentHits.contains(entry.chaosHit()),
                entry -> !recentTypes.contains(typeSnapshot.get(entry.chaosHit())),
                entry -> recentViewports.stream().noneMatch(recent -> isNear(entry.coordinates(), recent)));

        for (int kept = preferences.size(); kept > 0; kept--) {
            List<Predicate<CoordinateEntry>> active = preferences.subList(0, kept);
            List<CoordinateEntry> candidates = viewportListSnapshot.stream()
                    .filter(entry -> active.stream().allMatch(preference -> preference.test(entry)))
                    .toList();
            if (!candidates.isEmpty()) {
                return pickRandom(candidates);
            }
        }
        return pickRandom(viewportListSnapshot);
    }

    private Coordinates pickRandom(List<CoordinateEntry> entries) {
        return entries.get(RANDOM.nextInt(0, entries.size())).coordinates();
    }

    // in the order of the recent viewports, so the last ones are the latest
    private List<UUID> recentChaosHits(List<Coordinates> recentViewports) {
        Map<String, UUID> chaosHitSnapshot = blockChaosHits;
        return recentViewports.stream()
                .map(recent -> chaosHitSnapshot.get(BlockUtils.getKey(
                        Math.floorDiv(recent.x(), blockSize), Math.floorDiv(recent.y(), blockSize))))
                .filter(Objects::nonNull)
                .toList();
    }

    private boolean isNear(Coordinates a, Coordinates b) {
        int distance = RECENT_DISTANCE_BLOCKS * blockSize;
        return Math.abs(a.x() - b.x()) <= distance && Math.abs(a.y() - b.y()) <= distance;
    }
}
