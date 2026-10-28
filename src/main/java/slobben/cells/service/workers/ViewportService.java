package slobben.cells.service.workers;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import slobben.cells.dto.internal.Coordinates;
import slobben.cells.entities.Block;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ViewportService implements Worker {

    @Value("${cells.size.blockSize}")
    private int blockSize = 500;
    @Value("${cells.size.sectorSize}")
    private int sectorSize = 40;

    private final Map<String, Block> blocks;
    private volatile List<CoordinateEntry> interestingCoordinates = new ArrayList<>();

    private static final int COORDINATES_MAX = 20;
    private static final Random RANDOM = new Random();

    record CoordinateEntry(short count, Coordinates coordinates) {
    }

    @Override
    public void execute() {
        short lowestHighCount = 0;
        List<CoordinateEntry> newCoordinates = new ArrayList<>();

        for (Block block : blocks.values()) {
            short[][] sectorCount = block.getSectorCounts();

            for (int sx = 0; sx < sectorCount.length; sx++) {
                for (int sy = 0; sy < sectorCount.length; sy++) {
                    short count = sectorCount[sx][sy];

                    if ((newCoordinates.size() < COORDINATES_MAX || count > lowestHighCount)
                            // filters for a minimum threshold
                            && count > sectorSize + sectorSize
                            // filters sectors with just pure lines
                            && count % sectorSize != 0) {

                        if (newCoordinates.size() >= COORDINATES_MAX) {
                            newCoordinates.removeLast();
                        }

                        int worldX = block.getX() * blockSize + sx * sectorSize;
                        int worldY = block.getY() * blockSize + sy * sectorSize;

                        newCoordinates.add(new CoordinateEntry(count, new Coordinates(worldX, worldY)));
                        newCoordinates.sort(Comparator.comparing(CoordinateEntry::count).reversed());
                        lowestHighCount = newCoordinates.getLast().count();
                    }
                }
            }
        }
        interestingCoordinates = newCoordinates;
    }

    @Override
    public String getName() {
        return "Viewport Service";
    }

    public Coordinates getInterestingViewport() {
        List<CoordinateEntry> viewportListSnapshot = interestingCoordinates;
        if (viewportListSnapshot.isEmpty()) {
            return new Coordinates(0, 0);
        }
        return viewportListSnapshot.get(RANDOM.nextInt(0, viewportListSnapshot.size()))
                .coordinates();
    }
}
