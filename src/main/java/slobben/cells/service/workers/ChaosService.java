package slobben.cells.service.workers;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import slobben.cells.dto.BlockUpdate;
import slobben.cells.enums.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChaosService implements Worker {
    private final List<BlockUpdate> blockUpdates;
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
            chaosCounter = -squareSize;

            Pair<Integer, Integer> worldTarget = findTarget();
            Pair<Integer, Integer> target = Pair.of(worldTarget.getFirst() / blockSize, worldTarget.getSecond() / blockSize);
            Pair<Integer, Integer> returnTarget = null;
            log.info("Creating square with size: {}px at x: {}, y: {}", squareSize, target.getFirst(), target.getSecond());

            if (squareSize < blockSize) {
                boolean[][] cells = getSquare(squareSize, (blockSize - squareSize) / 2);
                blockUpdates.add(BlockUpdate.builder().x(target.getFirst()).y(target.getSecond()).state(cells).build());
                returnTarget = Pair.of(worldTarget.getFirst() + blockSize / 2, worldTarget.getSecond() + blockSize / 2);
            } else {
                int squareBlockSize = squareSize / blockSize + 1;
                int offset = squareSize % blockSize;
                log.info("Creating bigSquare with: blockSize{} and offset: {}", squareBlockSize, offset);
                List<BlockUpdate> newBlockUpdates = getBigSquare(squareBlockSize, offset);
                blockUpdates.addAll(newBlockUpdates.stream()
                        .map(update -> new BlockUpdate(update.x() + target.getFirst(), update.y() + target.getSecond(), update.state()))
                        .toList());

                // get first alive pixel
                BlockUpdate lastBlock = blockUpdates.getLast();
                for (int x = 0; x < blockSize; x++) {
                    for (int y = 0; y < blockSize; y++) {
                        if (lastBlock.state()[x][y]) {
                            returnTarget = Pair.of(lastBlock.x() * blockSize + x, lastBlock.y() * blockSize + y);
                            break;
                        }
                    }
                    if (returnTarget != null) {
                        break;
                    }
                }
            }
            addToLatestHits(returnTarget);
        }
    }

    public List<BlockUpdate> getBigSquare(int bigSquareSize, int offset) {
        List<BlockUpdate> returnList = new ArrayList<>();
        for (int x = 0; x < bigSquareSize; x++) {
            for (int y = 0; y < bigSquareSize; y++) {
                Direction blockDirection = getBorderDirection(x, y, 0, bigSquareSize);
                if (blockDirection == null) {
                    continue;
                }
                boolean[][] cells = new boolean[blockSize][blockSize];
                switch(blockDirection) {
                        case TOP_LEFT -> {
                            for (int cellY = 0; cellY < cells.length - offset; cellY++) {
                                cells[offset][cellY + offset] = true;
                                cells[offset + 1][cellY + offset] = true;
                            }
                            for (int cellX = 0; cellX < cells.length - offset; cellX++) {
                                cells[cellX + offset][offset] = true;
                                cells[cellX + offset][offset + 1] = true;
                            }
                        }
                        case TOP -> {
                            for (int cellY = 0; cellY < cells.length; cellY++) {
                                cells[offset][cellY] = true;
                                cells[offset + 1][cellY] = true;
                            }
                        }
                        case TOP_RIGHT -> {
                            for (int cellY = 0; cellY < cells.length - offset; cellY++) {
                                cells[offset][cellY] = true;
                                cells[offset + 1][cellY] = true;
                            }
                            for (int cellX = 0; cellX < cells.length - offset; cellX++) {
                                cells[cellX + offset][blockSize - 1 - offset] = true;
                                cells[cellX + offset][blockSize - 2 - offset] = true;
                            }
                        }
                        case LEFT -> {
                            for (int cellX = 0; cellX < cells.length; cellX++) {
                                cells[cellX][offset] = true;
                                cells[cellX][offset + 1] = true;
                            }
                        }
                        case RIGHT -> {
                            for (int cellX = 0; cellX < cells.length; cellX++) {
                                cells[cellX][blockSize - 1 - offset] = true;
                                cells[cellX][blockSize - 2 - offset] = true;
                            }
                        }
                        case BOTTOM_LEFT -> {
                            for (int cellY = 0; cellY < cells.length - offset; cellY++) {
                                cells[blockSize - 1 - offset][cellY + offset] = true;
                                cells[blockSize - 2 - offset][cellY + offset] = true;
                            }
                            for (int cellX = 0; cellX < cells.length - offset; cellX++) {
                                cells[cellX][offset] = true;
                                cells[cellX][offset + 1] = true;
                            }
                        }
                        case BOTTOM -> {
                            for (int cellY = 0; cellY < cells.length; cellY++) {
                                cells[blockSize - 1 - offset][cellY] = true;
                                cells[blockSize - 2 - offset][cellY] = true;
                            }
                        }
                        case BOTTOM_RIGHT -> {
                            for (int cellY = 0; cellY < cells.length - offset; cellY++) {
                                cells[blockSize - 1 - offset][cellY] = true;
                                cells[blockSize - 2 - offset][cellY] = true;
                            }
                            for (int cellX = 0; cellX < cells.length - offset; cellX++) {
                                cells[cellX][blockSize - 1 - offset] = true;
                                cells[cellX][blockSize - 2 - offset] = true;
                            }
                        }
                    }
                    returnList.add(BlockUpdate.builder().x(x).y(y).state(cells).build());
                }
        }
        return returnList;
    }


    private boolean[][] getSquare(int size, int offset) {
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
        return Pair.of(random.nextInt(-WORLD_TARGET_RANGE, WORLD_TARGET_RANGE), random.nextInt(-WORLD_TARGET_RANGE, WORLD_TARGET_RANGE));
    }

    private void addToLatestHits(Pair<Integer, Integer> target) {
        latestHits.addFirst(target);
        if (latestHits.size() > HIT_BUFFER_SIZE) {
            latestHits.removeLast();
        }
    }

    public Pair<Integer, Integer> getLatestHit() {
        return latestHits.getFirst();
    }

    public Pair<Integer, Integer> getOneOfLatestHits() {
        if (latestHits.isEmpty()) {
            return Pair.of(0, 0);
        }
        return latestHits.get(random.nextInt(0, latestHits.size()));
    }
}
