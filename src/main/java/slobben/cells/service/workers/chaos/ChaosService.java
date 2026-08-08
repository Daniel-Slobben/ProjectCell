package slobben.cells.service.workers.chaos;

import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import slobben.cells.dto.ChaosHitDto;
import slobben.cells.entities.model.Block;
import slobben.cells.service.WorldEditor;
import slobben.cells.service.workers.Worker;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChaosService implements Worker {
    private static final double SPIRAL_SPACING = 1000.0;
    private static final double ARC_LENGTH_PER_STEP = 7000.0;
    private static final Random random = new Random();

    private final WorldEditor worldEditor;
    private final Map<String, Block> blocks;
    private final List<ChaosHit> chaosHits;

    @Value("${cells.chaos.tics-to-spawn}")
    private int ticsToSpawn;
    @Value("${cells.chaos.enabled}")
    private boolean chaosEnabled;
    private int chaosCounter = 0;

    private int spiralGeneration = 1;
    @Value("${cells.chaos.max-hits}")
    private int maxHits;

    public String getName() {
        return "ChaosService";
    }

    public void execute() {
        if (!chaosEnabled) return;

        chaosHits.forEach(ChaosHit::incrementAge);

        chaosCounter++;

        if (chaosCounter > ticsToSpawn) {
            chaosCounter = 0;

            createChaos();
        }
    }

    private void createChaos() {
        Pair<Integer, Integer> worldTarget = calculateTarget(spiralGeneration++);
        ChaosType type = getWeightedRandomType();
        try {
            ChaosHit chaosHit = type.maker.getChaosHit(worldTarget.getFirst(), worldTarget.getSecond());
            assert chaosHit != null;
            worldEditor.setCells(worldTarget.getFirst(), worldTarget.getSecond(), chaosHit);

            chaosHits.addFirst(chaosHit);
            if (chaosHits.size() > maxHits) {
                clearChaosHit(chaosHits.getLast());
                chaosHits.removeLast();
            }
        } catch (Exception _) {
            log.error("Chaoshit exception handled! worldTarget: {}, type: {}", worldTarget, type);
        }
    }

    private void clearChaosHit(ChaosHit chaosHit) {
        List<String> keysToRemove = blocks.entrySet().stream()
                .filter(entrySet -> chaosHit.getId().equals(entrySet.getValue().getResponsibleChaosHit()))
                .map(Map.Entry::getKey)
                .toList();
        keysToRemove.forEach(blocks::remove);
        log.info("Cleaned ChaosHit {} with age {}, total blocks: {}", chaosHit.getId(), chaosHit.getAge(), keysToRemove.size());
    }

    private ChaosType getWeightedRandomType() {
        return switch (random.nextInt(0, 7)) {
            case 0, 1 -> ChaosType.LETTUCE;
            case 2, 3 -> ChaosType.SQUARE;
            case 4, 5 -> ChaosType.SQUARE_IN_SQUARE;
            case 6 -> ChaosType.GROWTH_PATTERN;
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
        if (chaosHits.isEmpty()) {
            if (chaosEnabled) {
                chaosCounter = ticsToSpawn;
                Pair<Integer, Integer> nextTarget = calculateTarget(spiralGeneration);
                return new ChaosHit(nextTarget.getFirst(), nextTarget.getSecond(), null, null);
            } else {
                return null;
            }
        }
        if (chaosHits.size() < 3) {
            return chaosHits.getFirst();
        }

        return chaosHits.subList(0, 3).getLast();
    }

    public ChaosHitDto getNextChaosHit(UUID id, boolean getNextHit) {
        ChaosHit currentChaosHit = chaosHits.stream().filter(hit -> hit.getId().equals(id)).findFirst().orElse(chaosHits.getFirst());

        int currentIndex = chaosHits.indexOf(currentChaosHit);
        try {
            return chaosHits.get(getNextHit ? currentIndex - 1 : currentIndex + 1).getDto();
        } catch (IndexOutOfBoundsException e) {
            return currentChaosHit.getDto();
        }
    }
}
