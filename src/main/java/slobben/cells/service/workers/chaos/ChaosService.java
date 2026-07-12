package slobben.cells.service.workers.chaos;

import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import slobben.cells.entities.model.Block;
import slobben.cells.service.WorldEditor;
import slobben.cells.service.workers.Worker;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChaosService implements Worker {
    private final WorldEditor worldEditor;
    private final Map<String, Block> blocks;

    private final List<ChaosHit> latestHits = new ArrayList<>();

    private static final double SPIRAL_SPACING = 500.0;
    private static final double ARC_LENGTH_PER_STEP = 5000.0;

    @Value("${cells.chaos.tics-to-spawn}")
    private int ticsToSpawn;
    @Value("${cells.chaos.enabled}")
    private boolean chaosEnabled;
    private static final Random random = new Random();

    private int chaosCounter = 0;

    private int spiralGeneration = 1;
    @Value("${cells.chaos.max-hits}")
    private int maxHits;

    public String getName() {
        return "ChaosService";
    }

    public void execute() {
        if (!chaosEnabled) return;

        latestHits.forEach(ChaosHit::incrementAge);

        chaosCounter++;

        if (chaosCounter > ticsToSpawn) {
            chaosCounter = 0;

            createChaos();
        }
    }

    private void createChaos() {
        Pair<Integer, Integer> worldTarget = calculateTarget(spiralGeneration++);
        ChaosType type = getWeightedRandomType();
        ChaosHit chaosHit = type.maker.getChaosHit(worldTarget.getFirst(), worldTarget.getSecond());
        assert chaosHit != null;
        worldEditor.setCells(worldTarget.getFirst(), worldTarget.getSecond(), chaosHit);

        latestHits.addFirst(chaosHit);
        if (latestHits.size() > maxHits) {
            clearChaosHit(latestHits.getLast());
            latestHits.removeLast();
        }
    }

    private void clearChaosHit(ChaosHit chaosHit) {
        List<String> keysToRemove = blocks.entrySet().stream()
                .filter(entrySet -> chaosHit.equals(entrySet.getValue().getResponsibleChaosHit()))
                .map(Map.Entry::getKey)
                .toList();
        keysToRemove.forEach(blocks::remove);
        log.info("Cleaned ChaosHit {} with age {}, total blocks: {}", chaosHit.getId(), chaosHit.getAge(), keysToRemove.size());
    }

    private ChaosType getWeightedRandomType() {
        return switch (random.nextInt(0, 10)) {
            case 0, 1, 2 -> ChaosType.LINE_MAKER;
            case 3, 4 -> ChaosType.SQUARE;
            case 5, 6 -> ChaosType.SQUARE_IN_SQUARE;
            case 7, 8 -> ChaosType.GROWTH_PATTERN;
            case 9 -> ChaosType.GROWTH_PATTERN;
            default -> throw new IllegalStateException("Unexpected value: " + random.nextInt(0, 10));
        };
    }

    public Pair<Integer, Integer> calculateTarget(int generation) {
        if (generation == 0) return Pair.of(0, 0);

        double theta = 1;
        for (int i = 0; i < generation; i++) {
            double r = Math.max(SPIRAL_SPACING * theta, 1.0);
            theta += ARC_LENGTH_PER_STEP / r;
        }

        double r = SPIRAL_SPACING * theta;
        int x = (int) Math.round(r * Math.cos(theta));
        int y = (int) Math.round(r * Math.sin(theta));

        return Pair.of(x, y);
    }

    public @Nullable ChaosHit getLatestHit() {
        if (latestHits.isEmpty()) {
            if (chaosEnabled) {
                chaosCounter = ticsToSpawn;
                Pair<Integer, Integer> nextTarget = calculateTarget(spiralGeneration);
                return new ChaosHit(nextTarget.getFirst(), nextTarget.getSecond(), null, null);
            } else {
                return null;
            }
        }
        return latestHits.getFirst();
    }

    public ChaosHit getNextChaosHit(UUID id, boolean getNextHit) {
        ChaosHit currentChaosHit = latestHits.stream()
                .filter(hit -> hit.getId().equals(id))
                .findFirst().orElse(latestHits.getFirst());

        int currentIndex = latestHits.indexOf(currentChaosHit);
        try {
            return latestHits.get(getNextHit ? currentIndex - 1 : currentIndex + 1);
        } catch (IndexOutOfBoundsException e) {
            return currentChaosHit;
        }
    }
}
