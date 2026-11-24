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
import slobben.cells.enums.ChaosCycle;
import slobben.cells.enums.ChaosType;
import slobben.cells.service.workers.chaosmakers.Maker;
import slobben.cells.util.BlockUtils;

import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChaosService extends Worker {
    private final Map<String, Block> blocks;
    private final Map<String, Block> newBlocks;
    private final BeanFactory beanFactory;
    private final List<ChaosHit> chaosHits;
    private final Random random;

    @Value("${cells.chaos.type:NULL}")
    private String preferredType = null;
    @Value("${cells.chaos.cycle:NULL}")
    private String preferredCycle = null;
    @Value("${cells.size.blockSize}")
    private int blockSize;
    @Value("${cells.chaos.max-blocks:500}")
    private int maxBlocks;
    @Value("${cells.targetspeed}")
    private int targetSpeed;
    @Value("${cells.chaos.max-cycle-share:0}")
    private double maxCycleShare;

    public static final int HITS_AT_START = 30;
    public static final int TICKS_BETWEEN_NEW_HITS = 200;
    private static final int OVERLOADED_CYCLES_BEFORE_RESET = 20;
    private int tickCounter = 0;
    private ChaosCycle cycle = ChaosCycle.MIXED;

    @PostConstruct
    void init() {
        kickstart();
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
            log.info("All chaos hits aged out, resetting the world");
            reset();
        } else if (isSimulationOverloaded()) {
            log.warn("{} cycles in a row took over {} of the target speed, resetting the world", OVERLOADED_CYCLES_BEFORE_RESET, maxCycleShare);
            reset();
        }

        if (tickCounter >= TICKS_BETWEEN_NEW_HITS && blocks.size() < maxBlocks) {
            tickCounter = 0;
            createChaosHit();
        }
    }

    // the last cycles all took over the share of targetspeed
    private boolean isSimulationOverloaded() {
        List<Long> deltas = serverInfoService.getLastTimeDeltas(OVERLOADED_CYCLES_BEFORE_RESET);
        return maxCycleShare > 0 && deltas.size() == OVERLOADED_CYCLES_BEFORE_RESET
                && deltas.stream().allMatch(delta -> delta + targetSpeed > maxCycleShare * targetSpeed);
    }

    private void reset() {
        blocks.clear();
        chaosHits.clear();
        kickstart();
    }

    // an empty world gets a burst of hits, after that they come slowly
    private void kickstart() {
        cycle = getCycle();
        log.info("Starting chaos cycle {}", cycle);
        for (int i = 0; i < HITS_AT_START; i++) {
            createChaosHit();
        }
        tickCounter = 0;
    }

    private void createChaosHit() {
        ChaosType type = getRandomType();
        Maker maker = beanFactory.getBean(type.maker);
        int size = cycle.getRandomSize(type, random);

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
            return cycle.getRandomType(random);
        }
    }

    private @NonNull ChaosCycle getCycle() {
        try {
            return ChaosCycle.valueOf(this.preferredCycle);
        } catch (IllegalArgumentException _) {
            return ChaosCycle.getRandomCycle(random);
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

    // a spot is free when no block lies within the hit or one block around it, counting the blocks hits wrote this tick
    private boolean isFree(int x, int y, int size) {
        int half = size / 2;
        for (int blockX = Math.floorDiv(x - half, blockSize) - 1; blockX <= Math.floorDiv(x + half, blockSize) + 1; blockX++) {
            for (int blockY = Math.floorDiv(y - half, blockSize) - 1; blockY <= Math.floorDiv(y + half, blockSize) + 1; blockY++) {
                String key = BlockUtils.getKey(blockX, blockY);
                if (blocks.containsKey(key) || newBlocks.containsKey(key)) {
                    return false;
                }
            }
        }
        return true;
    }
}
