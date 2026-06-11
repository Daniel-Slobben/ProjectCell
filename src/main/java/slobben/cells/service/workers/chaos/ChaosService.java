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

@Service
@RequiredArgsConstructor
@Slf4j
public class ChaosService implements Worker {
    private final WorldEditor worldEditor;
    private final SquareMaker squareMaker;
    private final List<ChaosHit> latestHits = new ArrayList<>();
    private final OscillatorMaker oscillatorMaker;
    private static final int SPIRAL_JUMP_LENGTH = 5000;
    private final SquareInSquareMaker squareInSquareMaker;
    @Value("${cells.chaos.tics-to-spawn}")
    private int ticsToSpawn;
    @Value("${cells.chaos.enabled}")
    private boolean chaosEnabled;
    private int chaosCounter = 0;
    private final LineMaker lineMaker;
    private int spiralGeneration = 1;

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
            case SQUARE_IN_SQUARE -> squareInSquareMaker.getChaosHit(worldTarget.getFirst(), worldTarget.getSecond());
            case LINE_MAKER -> lineMaker.getChaosHit(worldTarget.getFirst(), worldTarget.getSecond());
            case GROWTH_PATTERN -> oscillatorMaker.getChaosHit(worldTarget.getFirst(), worldTarget.getSecond());
            case OSCILLATORS -> null;
        };
        assert chaosHit != null;
        worldEditor.setCells(worldTarget.getFirst(), worldTarget.getSecond(), chaosHit.getPattern());

        latestHits.add(chaosHit);
    }

    private ChaosType getWeightedRandomType() {
        return ChaosType.SQUARE_IN_SQUARE;
    }

    private Pair<Integer, Integer> findTarget() {
        return calculateTarget(spiralGeneration++);
    }

    private Pair<Integer, Integer> peekTarget() {
        return calculateTarget(spiralGeneration);
    }

    public Pair<Integer, Integer> calculateTarget(int generation) {
        int currentX = 0;
        int currentY = 0;

        for (int i = 0; i < generation; i++) {
            switch (i % 4) {
                case 0 -> currentX += (SPIRAL_JUMP_LENGTH * i);
                case 1 -> currentY += (SPIRAL_JUMP_LENGTH * i);
                case 2 -> currentX -= (SPIRAL_JUMP_LENGTH * i);
                case 3 -> currentY -= (SPIRAL_JUMP_LENGTH * i);
                default -> throw new IllegalStateException();
            }
        }
        return Pair.of(currentX, currentY);
    }

    public Optional<ChaosHit> getLatestHit() {
        if (latestHits.isEmpty()) {
            if (chaosEnabled) {
                chaosCounter = ticsToSpawn;
                Pair<Integer, Integer> nextTarget = peekTarget();
                return Optional.of(new ChaosHit(nextTarget.getFirst(), nextTarget.getSecond(), null, null));
            } else {
                return Optional.empty();
            }
        }
        return Optional.of(latestHits.getFirst());
    }
}
