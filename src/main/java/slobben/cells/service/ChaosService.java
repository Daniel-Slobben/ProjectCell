package slobben.cells.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import slobben.cells.config.BlockUpdate;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ChaosService {
    private final EnvironmentService environmentService;

    private static final int BLOCK_TARGET_RANGE_X = 5000;
    private static final int BLOCK_TARGET_RANGE_Y = 5000;
    private static final Random random = new Random();
    private static final int SQUARE_MAX = 60 * 30;
    private int squareCounter = SQUARE_MAX ;

    private static final int HIT_BUFFER_SIZE = 100;
    private ArrayList<Pair<Integer, Integer>> latestHits = new ArrayList<>(HIT_BUFFER_SIZE);

    public List<BlockUpdate> tic() {
        List<BlockUpdate> returnList = new ArrayList<>();
        squareCounter++;

        if (squareCounter > SQUARE_MAX ) {
            squareCounter = 0;
            Pair<Integer, Integer> target = findTarget();

            int size = random.nextInt(50, environmentService.getBlockSize());
            boolean[][] cells = getSquare(size, (environmentService.getBlockSize() - size) / 2);
            returnList.add(BlockUpdate.builder().x(target.getFirst()).y(target.getSecond()).state(cells).build());

            addToLatestHits(target);
        }
        return returnList;
    }

    private void addToLatestHits(Pair<Integer, Integer> target) {
        latestHits.add(target);
        if (latestHits.size() > HIT_BUFFER_SIZE) {
            latestHits.removeLast();
        }
    }

    private boolean[][] getSquare(int size, int offset) {
       int blockSize = environmentService.getBlockSize();
       if (size/2 + offset > blockSize) {
           throw new IllegalArgumentException("Square is out off the block size. Size/2 + offset = %s, block size = %s".formatted(size/2 + offset, blockSize));
       }
       boolean[][] square = new boolean[blockSize][blockSize];

       int maxRange = size + offset;

       for (int x = offset; x < maxRange; x++) {
           for (int y = offset; y < maxRange; y++) {
               if (x == offset || y == offset || x == maxRange - 1 || y == maxRange - 1 ||
               x == offset + 1 || y == offset + 1 || x == maxRange - 2 || y == maxRange- 2) {
                   square[x][y] = true;
               }
           }
       }
       return square;
    }

    private Pair<Integer, Integer> findTarget() {
        return Pair.of(random.nextInt(10, BLOCK_TARGET_RANGE_X), random.nextInt(10, BLOCK_TARGET_RANGE_Y));
    }

    public Pair<Integer, Integer> getOneOfLatestHits() {
        if (latestHits.isEmpty()) {
            return Pair.of(0, 0);
        }
        return latestHits.get(random.nextInt(0, latestHits.size()));
    }
}
