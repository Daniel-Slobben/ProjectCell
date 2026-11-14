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
public class ChaosService extends Worker {
    private final Map<String, Block> blocks;
    private final BeanFactory beanFactory;
    private final List<ChaosHit> chaosHits;

    private static final int MIN_SIZE = 2000;
    private static final int MAX_SIZE = 16000;

    private final Random random = new Random();

    @Value("${cells.chaos.type:NULL}")
    private String preferredType = null;
    @Value("${cells.size.blockSize}")
    private int blockSize;
    @Value("${cells.chaos.max-blocks:500}")
    private int maxBlocks;
    @Value("${cells.targetspeed}")
    private int targetSpeed;
    // the share of targetspeed a cycle may take, 0 turns the guard off
    @Value("${cells.chaos.max-cycle-share:0}")
    private double maxCycleShare;

    private static final int TICKS_BETWEEN_NEW_HITS = 100;
    private static final int OVERLOADED_CYCLES_BEFORE_RESET = 20;
    private int tickCounter = 0;
    private int overloadedCycles = 0;

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
        chaosHits.removeIf(hit -> !hit.isActive());

        if (chaosHits.isEmpty()) {
            log.info("All chaos hits aged out, resetting the world");
            reset();
        } else if (overloadedCycles >= OVERLOADED_CYCLES_BEFORE_RESET) {
            log.warn("{} cycles in a row took over {} of the target speed, resetting the world", overloadedCycles, maxCycleShare);
            reset();
        }

        if (tickCounter >= TICKS_BETWEEN_NEW_HITS && blocks.size() < maxBlocks && overloadedCycles == 0) {
            tickCounter = 0;
            createChaosHit();
        }
    }

    public void recordCycleTime(long millis) {
        if (maxCycleShare > 0 && millis > maxCycleShare * targetSpeed) {
            overloadedCycles++;
        } else {
            overloadedCycles = 0;
        }
    }

    private void reset() {
        blocks.clear();
        chaosHits.clear();
        overloadedCycles = 0;
        tickCounter = TICKS_BETWEEN_NEW_HITS;
    }

    private void createChaosHit() {
        ChaosType type = getRandomType();
        Maker maker = beanFactory.getBean(type.maker);

        double uniform = random.nextDouble();
        double skewed = Math.pow(uniform, 3.0);
        int size = MIN_SIZE + (int) (skewed * (MAX_SIZE - MIN_SIZE));

        Coordinates coordinates = findFreeSpot(size);

        ChaosHit chaosHit = maker.getChaosHit(coordinates.x(), coordinates.y(), size);
        chaosHit.setType(type);
        chaosHits.addFirst(chaosHit);
        log.info("New chaos hit: {}, {} blocks in the world", chaosHit.getName(), blocks.size());
    }

    private @NonNull ChaosType getRandomType() {
        try {
            return ChaosType.valueOf(this.preferredType);
        } catch (IllegalArgumentException _) {
            return ChaosType.getRandomType();
        }
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
}
