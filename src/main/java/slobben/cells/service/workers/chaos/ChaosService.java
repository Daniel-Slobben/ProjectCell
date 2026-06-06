package slobben.cells.service.workers.chaos;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import slobben.cells.service.WorldEditor;
import slobben.cells.service.workers.Worker;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChaosService implements Worker {
    private static final Random random = new Random();
    private final WorldEditor worldEditor;
    private final SquareMaker squareMaker;
    private final List<ChaosHit> latestHits = new ArrayList<>();
    private final OscillatorMaker oscillatorMaker;
    @Value("${cells.chaos.world-target-range}")
    private int worldTargetRange;
    @Value("${cells.chaos.tics-to-spawn}")
    private int ticsToSpawn;
    @Value("${cells.chaos.enabled}")
    private boolean chaosEnabled;
    private int chaosCounter = 0;

    public String getName() {
        return "ChaosService";
    }

    public void execute() {
        if (!chaosEnabled) return;

        chaosCounter++;

        if (chaosCounter > ticsToSpawn) {
            chaosCounter = 0;

            createChaos();
        }
    }

    private void createChaos() {
        Pair<Integer, Integer> worldTarget = findTarget();

        ChaosType type = getWeightedRandomType();
        ChaosHit chaosHit = switch (type) {
            case SQUARE -> squareMaker.getChaosHit(worldTarget.getFirst(), worldTarget.getSecond());
            case GROWTH_PATTERN -> oscillatorMaker.getChaosHit(worldTarget.getFirst(), worldTarget.getSecond());
            case OSCILLATORS -> null;
        };
        assert chaosHit != null;
        worldEditor.setCells(worldTarget.getFirst(), worldTarget.getSecond(), chaosHit.getPattern());

        latestHits.add(chaosHit);
    }

    private ChaosType getWeightedRandomType() {
        return ChaosType.GROWTH_PATTERN;
    }

    private Pair<Integer, Integer> findTarget() {
        return Pair.of(random.nextInt(-worldTargetRange, worldTargetRange), random.nextInt(-worldTargetRange, worldTargetRange));
    }

    public Optional<ChaosHit> getLatestHit() {
        if (latestHits.isEmpty()) {
            if (chaosEnabled) {
                createChaos();
                return getLatestHit();
            } else {
                return Optional.empty();
            }
        }
        return Optional.of(latestHits.getFirst());
    }
}
