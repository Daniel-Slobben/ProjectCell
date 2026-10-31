package slobben.cells.service.workers;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import slobben.cells.dto.internal.Coordinates;
import slobben.cells.entities.Block;
import slobben.cells.entities.ChaosHit;
import slobben.cells.enums.ChaosType;
import slobben.cells.service.workers.chaosmakers.Maker;

import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChaosService implements Worker {
    private final Map<String, Block> blocks;
    private final BeanFactory beanFactory;
    private final List<ChaosHit> chaosHits;

    private static final int MIN_SIZE = 2000;
    private static final int MAX_SIZE = 30000;

    private final Random random = new Random();

    private ChaosType lastType;
    @Value("${cells.chaos.type:NULL}")
    private String preferredType = null;
    @Value("${cells.size.blockSize}")
    private int blockSize;

    private static final long PIXEL_COUNT_TARGET = 2_000_000_000;
    private static final int TICKS_BETWEEN_NEW_HITS = 100;
    private long currentPixelCount = 0;
    private int tickCounter = 0;

    @PostConstruct
    void init() {
        createChaosHit();
    }

    public String getName() {
        return "ChaosService";
    }

    public void execute() {
        tickCounter++;
        for (ChaosHit hit : chaosHits) {
            hit.incrementAge();
        }

        if (chaosHits.stream().noneMatch(ChaosHit::isActive)) {
            blocks.clear();
            chaosHits.clear();
            currentPixelCount = 0;
            tickCounter = TICKS_BETWEEN_NEW_HITS + 1;
        }

        if (tickCounter > TICKS_BETWEEN_NEW_HITS
                && currentPixelCount < PIXEL_COUNT_TARGET) {
            tickCounter = 0;
            createChaosHit();
        }
    }

    private void createChaosHit() {
        Maker maker = getRandomMaker();

        double uniform = random.nextDouble();
        double skewed = Math.pow(uniform, 2.0);
        int size = MIN_SIZE + (int) (skewed * (MAX_SIZE - MIN_SIZE));

        Coordinates coordinates = findFreeSpot(size);

        ChaosHit chaosHit = maker.getChaosHit(coordinates.x(), coordinates.y(), size);
        chaosHits.addFirst(chaosHit);
        currentPixelCount += (long) size * size;
    }

    private @NonNull Maker getRandomMaker() {
        ChaosType type;
        try {
            type = ChaosType.valueOf(this.preferredType);
        } catch (IllegalArgumentException _) {
            type = getWeightedRandomType();
        }
        return beanFactory.getBean(type.maker);
    }


    public Coordinates findFreeSpot(final int size) {
        int searchRadius = size;
        int step = size / 2;
        int attemptsPerRadius = 10;

        while (true) {
            for (int i = 0; i < attemptsPerRadius; i++) {
                int x = random.nextInt(-searchRadius, searchRadius + 1);
                int y = random.nextInt(-searchRadius, searchRadius + 1);
                if (isFree(x, y, size)) {
                    return new Coordinates(x, y);
                }
            }
            searchRadius += step;
            attemptsPerRadius += 2;
        }
    }

    private boolean isFree(int x, int y, int size) {
        for (ChaosHit hit : chaosHits) {
            Coordinates c = hit.getCurrentStartingView();
            int minDistTimesTwo = hit.getSize() + size + blockSize * 2;
            if (2 * Math.abs(x - c.x()) < minDistTimesTwo
                    && 2 * Math.abs(y - c.y()) < minDistTimesTwo) {
                return false;
            }
        }
        return true;
    }

    private ChaosType getWeightedRandomType() {
        ChaosType type = switch (random.nextInt(0, 11)) {
            case 0, 1, 7, 8, 9 -> ChaosType.SQUARE;
            case 2, 3, 4, 5, 6 -> ChaosType.DIAGONAL_LINES;
            case 10 -> ChaosType.GROWTH_PATTERN;
            default -> throw new IllegalStateException("Unexpected value: " + random.nextInt(0, 10));
        };

        // don't generate the same type twice in a row
        if (type == lastType) {
            return getWeightedRandomType();
        }
        this.lastType = type;
        return type;
    }
}
