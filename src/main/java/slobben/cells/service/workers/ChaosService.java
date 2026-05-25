package slobben.cells.service.workers;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import slobben.cells.entities.Pattern;
import slobben.cells.service.WorldEditor;

import java.util.ArrayList;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChaosService implements Worker {
    private final WorldEditor worldEditor;

    @Value("${cells.size.blockSize}")
    private int blockSize;

    private static final int HIT_BUFFER_SIZE = 10;
    @Value("${cells.chaos.world-target-range}")
    private int WORLD_TARGET_RANGE;
    @Value("${cells.chaos.square-size-min}")
    private int SQUARE_SIZE_MIN;
    private static final Random random = new Random();
    @Value("${cells.chaos.square-size-max}")
    private int SQUARE_SIZE_MAX;
    @Value("${cells.chaos.tics-to-spawn}")
    private int ticsToSpawn;
    @Value("${cells.chaos.enabled}")
    private boolean chaosEnabled;
    private int chaosCounter;

    private final ArrayList<Pair<Integer, Integer>> latestHits = new ArrayList<>(HIT_BUFFER_SIZE);

    @PostConstruct
    void init() {
        this.chaosCounter = ticsToSpawn;
    }

    public String getName() {
        return "ChaosService";
    }

    public void execute() {
        if (!chaosEnabled) return;

        chaosCounter++;

        if (chaosCounter > ticsToSpawn) {
            int squareSize = random.nextInt(SQUARE_SIZE_MIN, SQUARE_SIZE_MAX);
            chaosCounter = 0;

            Pair<Integer, Integer> worldTarget = findTarget();
            Pair<Integer, Integer> target = Pair.of(worldTarget.getFirst() / blockSize, worldTarget.getSecond() / blockSize);
            log.info("Creating square with size: {}px at x: {}, y: {}", squareSize, target.getFirst(), target.getSecond());

            boolean[][] patternMatrix = new boolean[squareSize][squareSize];
            for (int i = 0; i < squareSize; i++) {
                patternMatrix[0][i] = true;
                patternMatrix[1][i] = true;

                patternMatrix[squareSize - 1][i] = true;
                patternMatrix[squareSize - 2][i] = true;

                patternMatrix[i][0] = true;
                patternMatrix[i][1] = true;

                patternMatrix[i][squareSize - 1] = true;
                patternMatrix[i][squareSize - 2] = true;
            }
            Pattern pattern = Pattern.builder()
                    .x(squareSize)
                    .y(squareSize)
                    .matrix(patternMatrix)
                    .build();
            worldEditor.setCells(worldTarget.getFirst(), worldTarget.getSecond(), pattern);

            addToLatestHits(worldTarget);
        }
    }

    private Pair<Integer, Integer> findTarget() {
        return Pair.of(random.nextInt(-WORLD_TARGET_RANGE, WORLD_TARGET_RANGE), random.nextInt(-WORLD_TARGET_RANGE, WORLD_TARGET_RANGE));
    }

    private void addToLatestHits(Pair<Integer, Integer> target) {
        latestHits.addFirst(target);
        if (latestHits.size() > HIT_BUFFER_SIZE) {
            latestHits.removeLast();
        }
    }

    public Pair<Integer, Integer> getLatestHit() {
        if (latestHits.isEmpty()) {
            return Pair.of(0, 0);
        }
        return latestHits.getFirst();
    }

    public Pair<Integer, Integer> getOneOfLatestHits() {
        if (latestHits.isEmpty()) {
            return Pair.of(0, 0);
        }
        return latestHits.get(random.nextInt(0, latestHits.size()));
    }
}
