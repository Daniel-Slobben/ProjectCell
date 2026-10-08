package slobben.cells.service.workers;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import slobben.cells.dto.internal.Coordinates;
import slobben.cells.entities.Block;
import slobben.cells.enums.Direction;
import slobben.cells.service.ExecutorService;
import slobben.cells.util.BlockUtils;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static slobben.cells.util.BlockUtils.getKey;

@RequiredArgsConstructor
@Service
public class Stitching implements Worker {
    private final ExecutorService executorService;
    private final Map<String, Block> blocks;
    private final Map<String, Block> newBlocks;
    @Value("${cells.size.blockSize}")
    private int blockSize;

    private static boolean hasTrueValue(boolean[] cells) {
        for (var cell : cells) {
            if (cell) return true;
        }
        return false;
    }

    private static boolean[] getColumnCells(boolean[][] cells, int srcCol) {
        boolean[] cellsToCopy = new boolean[cells.length - 2];
        for (int i = 1; i < cells.length - 1; i++) {
            cellsToCopy[i - 1] = cells[i][srcCol];
        }
        return cellsToCopy;
    }

    @Override
    public String getName() {
        return "Adding bordercells";
    }

    public void execute() {
        blocks.values().parallelStream().forEach(this::clearBlockBorders);

        Set<Runnable> tasks = blocks.values().stream().map(block -> (Runnable) () -> addBorderCells(block)).collect(Collectors.toSet());
        executorService.executeTasksParallel(tasks, getName());
    }

    private void clearBlockBorders(Block block) {
        boolean[][] cells = block.getCells();
        int max = blockSize + 1;
        for (int i = 0; i < cells[0].length; i++) {
            cells[0][i] = false;
            cells[max][i] = false;

            // y keys
            cells[i][0] = false;
            cells[i][max] = false;
        }
    }

    private void addBorderCells(Block block) {
        Arrays.stream(Direction.values()).forEach(direction -> {
            int neighborX = block.getX() + direction.getDx();
            int neighborY = block.getY() + direction.getDy();

            String neighborKey = getKey(neighborX, neighborY);
            setBorderCellsForDirection(neighborKey, direction, block);
        });
    }

    private void setBorderCellsForDirection(String key, Direction direction, Block block) {
        switch (direction) {
            case LOW_X_LOW_Y -> {
                boolean cell = block.getCells()[1][1];
                if (cell) {
                    operateOnBlock(key, block.getResponsibleChaosHit(), (Block b) -> b.getCells()[blockSize + 1][blockSize + 1] = true);
                }
            }
            case LOW_X_MID_Y -> {
                boolean[] cellsToCopy = new boolean[blockSize];
                System.arraycopy(block.getCells()[1], 1, cellsToCopy, 0, blockSize);

                if (hasTrueValue(cellsToCopy)) {
                    operateOnBlock(key, block.getResponsibleChaosHit(), (Block b) -> System.arraycopy(cellsToCopy, 0, b.getCells()[blockSize + 1], 1, blockSize));
                }
            }
            case LOW_X_HIGH_Y -> {
                boolean cell = block.getCells()[1][blockSize];
                if (cell) {
                    operateOnBlock(key, block.getResponsibleChaosHit(), (Block b) -> b.getCells()[blockSize + 1][0] = true);
                }
            }
            case MID_X_LOW_Y -> {
                boolean[] cellsToCopy = getColumnCells(block.getCells(), 1);

                if (hasTrueValue(cellsToCopy)) {

                    operateOnBlock(key, block.getResponsibleChaosHit(), (Block b) -> {
                        for (int i = 0; i < blockSize; i++) {
                            b.getCells()[i + 1][blockSize + 1] = cellsToCopy[i];
                        }
                    });
                }
            }
            case MID_X_HIGH_Y -> {
                boolean[] cellsToCopy = getColumnCells(block.getCells(), blockSize);

                if (hasTrueValue(cellsToCopy)) {
                    operateOnBlock(key, block.getResponsibleChaosHit(), (Block b) -> {
                        for (int i = 0; i < blockSize; i++) {
                            b.getCells()[i + 1][0] = cellsToCopy[i];
                        }
                    });
                }
            }
            case HIGH_X_LOW_Y -> {
                boolean cell = block.getCells()[blockSize][1];
                if (cell) {
                    operateOnBlock(key, block.getResponsibleChaosHit(), (Block b) -> b.getCells()[0][blockSize + 1] = true);
                }
            }
            case HIGH_X_MID_Y -> {
                boolean[] cellsToCopy = new boolean[blockSize];
                System.arraycopy(block.getCells()[blockSize], 1, cellsToCopy, 0, blockSize);

                if (hasTrueValue(cellsToCopy)) {
                    operateOnBlock(key, block.getResponsibleChaosHit(), (Block b) -> System.arraycopy(cellsToCopy, 0, b.getCells()[0], 1, blockSize));
                }
            }
            case HIGH_X_HIGH_Y -> {
                boolean cell = block.getCells()[blockSize][blockSize];
                if (cell) {
                    operateOnBlock(key, block.getResponsibleChaosHit(), (Block b) -> b.getCells()[0][0] = true);
                }
            }
        }
    }

    private synchronized void operateOnBlock(String key, UUID responsibleHit, Consumer<Block> task) {
        Block block;
        block = blocks.get(key);
        if (block == null) {
            block = newBlocks.computeIfAbsent(key, _ -> {
                Coordinates coordinates = BlockUtils.resolveKey(key);
                return new Block(coordinates.x(), coordinates.y(), responsibleHit, blockSize);
            });
        }
        task.accept(block);
    }

}