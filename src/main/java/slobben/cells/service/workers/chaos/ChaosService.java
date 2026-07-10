package slobben.cells.service.workers.chaos;

import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import slobben.cells.entities.Pattern;
import slobben.cells.service.WorldEditor;
import slobben.cells.service.workers.Worker;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChaosService implements Worker {
    private final WorldEditor worldEditor;

    private final List<ChaosHit> latestHits = new ArrayList<>();
    private static final int SPIRAL_JUMP_LENGTH = 5000;
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
        worldEditor.setCells(worldTarget.getFirst(), worldTarget.getSecond(), chaosHit.getPattern());

        latestHits.addFirst(chaosHit);
        if (latestHits.size() > maxHits) {
            clearChaosHit(latestHits.getLast());
            latestHits.removeLast();
        }
    }

    private void clearChaosHit(ChaosHit chaosHit) {
        final long xOffset = chaosHit.getPattern().x() / 2;
        final int clearSizeX = chaosHit.getPattern().x() * 2;

        final long yOffset = chaosHit.getPattern().y() / 2;
        final int clearSizeY = chaosHit.getPattern().y() * 2;

        Pattern pattern = new Pattern("clear", clearSizeX, clearSizeY, new boolean[clearSizeX][clearSizeY]);
        worldEditor.setCells(chaosHit.getWorldX() - xOffset, chaosHit.getWorldY() - yOffset, pattern);
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
                .findFirst().orElseThrow(IllegalArgumentException::new);

        int currentIndex = latestHits.indexOf(currentChaosHit);
        try {
            return latestHits.get(getNextHit ? currentIndex - 1 : currentIndex + 1);
        } catch (IndexOutOfBoundsException e) {
            return currentChaosHit;
        }
    }
}
