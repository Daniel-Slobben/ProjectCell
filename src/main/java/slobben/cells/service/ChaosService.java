package slobben.cells.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import slobben.cells.config.BlockUpdate;
import slobben.cells.enums.Direction;

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

    @Value("${properties.big-square-size}")
    private int bigSquareSize;

    @Value("${properties.chaos.enabled}")
    private boolean chaosEnabled;

    private static final int HIT_BUFFER_SIZE = 100;
    private final ArrayList<Pair<Integer, Integer>> latestHits = new ArrayList<>(HIT_BUFFER_SIZE);

    public List<BlockUpdate> tic() {
        if (!chaosEnabled) return Collections.emptyList();

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

    public List<BlockUpdate> getBigSquare() {
        int offset = 0;
        int maxRange = this.bigSquareSize + offset;

        int blockSize = environmentService.getBlockSize();
        List<BlockUpdate> returnList = new ArrayList<>();
        for (int x = offset; x < maxRange; x++) {
            for (int y = offset; y < maxRange; y++) {
                Direction blockDirection = getBorderDirection(x, y, offset, maxRange);
                if (blockDirection == null) {
                    continue;
                }
                boolean[][] cells = new boolean[blockSize][blockSize];
                switch(blockDirection) {
                        case TOP_LEFT -> {
                            for (int cellY = 0; cellY < cells.length; cellY++) {
                                cells[0][cellY] = true;
                                cells[1][cellY] = true;
                            }
                            for (int cellX = 0; cellX < cells.length; cellX++) {
                                cells[cellX][0] = true;
                                cells[cellX][1] = true;
                            }
                        }
                        case TOP -> {
                            for (int cellY = 0; cellY < cells.length; cellY++) {
                                cells[0][cellY] = true;
                                cells[1][cellY] = true;
                            }
                        }
                        case TOP_RIGHT -> {
                            for (int cellY = 0; cellY < cells.length; cellY++) {
                                cells[0][cellY] = true;
                                cells[1][cellY] = true;
                            }
                            for (int cellX = 0; cellX < cells.length; cellX++) {
                                cells[cellX][blockSize - 1] = true;
                                cells[cellX][blockSize - 2] = true;
                            }
                        }
                        case LEFT -> {
                            for (int cellX = 0; cellX < cells.length; cellX++) {
                                cells[cellX][0] = true;
                                cells[cellX][1] = true;
                            }
                        }
                        case RIGHT -> {
                            for (int cellX = 0; cellX < cells.length; cellX++) {
                                cells[cellX][blockSize - 1] = true;
                                cells[cellX][blockSize - 2] = true;
                            }
                        }
                        case BOTTOM_LEFT -> {
                            for (int cellY = 0; cellY < cells.length; cellY++) {
                                cells[blockSize - 1][cellY] = true;
                                cells[blockSize - 2][cellY] = true;
                            }
                            for (int cellX = 0; cellX < cells.length; cellX++) {
                                cells[cellX][0] = true;
                                cells[cellX][1] = true;
                            }
                        }
                        case BOTTOM -> {
                            for (int cellY = 0; cellY < cells.length; cellY++) {
                                cells[blockSize - 1][cellY] = true;
                                cells[blockSize - 2][cellY] = true;
                            }
                        }
                        case BOTTOM_RIGHT -> {
                            for (int cellY = 0; cellY < cells.length; cellY++) {
                                cells[blockSize - 1][cellY] = true;
                                cells[blockSize - 2][cellY] = true;
                            }
                            for (int cellX = 0; cellX < cells.length; cellX++) {
                                cells[cellX][blockSize - 1] = true;
                                cells[cellX][blockSize - 2] = true;
                            }
                        }
                    }
                    returnList.add(BlockUpdate.builder().x(x).y(y).state(cells).build());
                }
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
               if (getBorderDirection(x, y, offset, maxRange) != null ||
                       getBorderDirection(x, y, offset + 1, maxRange - 2) != null) {
                   square[x][y] = true;
               }
           }
       }
       return square;
    }

    private Direction getBorderDirection(int x, int y, int offset, int maxRange) {
        int dx = edgeOrMiddle(x, offset, maxRange);
        int dy = edgeOrMiddle(y, offset, maxRange);

        if (dx == 0 && dy == 0) return null;

        return Direction.from(dx, dy);
    }

    private int edgeOrMiddle(int value, int offset, int maxRange) {
        if (value == offset)   return -1;
        if (value == maxRange-1)   return  1;
        return 0;
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
