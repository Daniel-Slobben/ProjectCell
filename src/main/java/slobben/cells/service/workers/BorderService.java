package slobben.cells.service.workers;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import slobben.cells.config.EnvironmentConfig;
import slobben.cells.dto.BlockUpdate;
import slobben.cells.entities.model.Block;
import slobben.cells.entities.model.BorderInfo;
import slobben.cells.enums.Direction;
import slobben.cells.service.ExecutorService;
import slobben.cells.util.BlockUtils;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static slobben.cells.util.BlockUtils.getKey;

@RequiredArgsConstructor
@Service
public class BorderService implements Worker {
    private final EnvironmentConfig environmentConfig;
    private final ExecutorService executorService;
    private final Map<String, Block> blocks;
    private int blockSizeWithBorder;
    private final Map<String, BlockUpdate> blockUpdates;
    private final Map<String, BorderInfo> bordersMap;
    @Value("${cells.size.blockSize}")
    private int blockSize;

    Map<String, BorderInfo> newBorderMaps = new ConcurrentHashMap<>();

    @PostConstruct
    private void postConstruct() {
        blockSizeWithBorder = environmentConfig.getBlockSizeWithBorder();
    }

    @Override
    public String getName() {
        return "Adding bordercells";
    }

    public void execute() {
        bordersMap.clear();
        newBorderMaps.clear();

        blocks.values().forEach(block -> bordersMap.put(getKey(block.getX(), block.getY()), new BorderInfo(blockSize, block.getResponsibleChaosHit())));

        Set<Runnable> tasks = blocks.values().stream().map(block -> (Runnable) () -> addBorderCells(block)).collect(Collectors.toSet());
        executorService.executeTasksParallel(tasks);

        newBorderMaps.entrySet().stream()
                .filter(entry -> entry.getValue().isHasAliveCells())
                .forEach(entry -> {
                    Pair<Integer, Integer> coorPair = BlockUtils.resolveKey(entry.getKey());
                    BlockUpdate blockUpdate = new BlockUpdate(coorPair.getFirst(), coorPair.getSecond(), new boolean[blockSize][blockSize], entry.getValue().getResponsibleChaosHit());
                    blockUpdates.put(blockUpdate.getKey(), blockUpdate);
                });
    }

    public void addBorderCells(Block block) {
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                if (i == 0 && j == 0) continue;

                int neighborX = block.getX() + i;
                int neighborY = block.getY() + j;
                String neighborKey = getKey(neighborX, neighborY);

                BorderInfo neighborMap;
                synchronized (this) {
                    neighborMap = bordersMap.get(neighborKey);
                    if (neighborMap == null) {
                        neighborMap = new BorderInfo(blockSize, block.getResponsibleChaosHit());
                        bordersMap.put(neighborKey, neighborMap);
                        newBorderMaps.put(BlockUtils.getKey(neighborX, neighborY), neighborMap);
                    }
                }

                boolean hasLiveCells = setBorderCellsForDirection(neighborMap, i, j, block.getCells());
                if (hasLiveCells) {
                    neighborMap.setHasAliveCells(true);
                }

            }
        }
    }

    private boolean setBorderCellsForDirection(BorderInfo neighbourMap, int i, int j, boolean[][] cells) {
        Direction direction = Direction.from(i, j);
        assert direction != null;

        switch (direction) {
            case TOP_LEFT -> {
                boolean cell = cells[1][1];
                neighbourMap.setBottomRightCorner(cell);
                return cell;
            }
            case TOP -> {
                boolean[] cellsToCopy = cells[1];
                var result = neighbourMap.setBottomBorder(cellsToCopy);
                return hasTrueValue(result);
            }
            case TOP_RIGHT -> {
                boolean cell = cells[1][blockSize];
                neighbourMap.setBottomLeftCorner(cell);
                return cell;
            }
            case LEFT -> {
                var result = getColumnCells(cells, 1);
                neighbourMap.setRightBorder(result.getFirst());
                return result.getSecond();
            }
            case RIGHT -> {
                var result = getColumnCells(cells, blockSize);
                neighbourMap.setLeftBorder(result.getFirst());
                return result.getSecond();
            }
            case BOTTOM_LEFT -> {
                boolean cell = cells[blockSize][1];
                neighbourMap.setTopRightCorner(cell);
                return cell;
            }
            case BOTTOM -> {
                var cellsToCopy = new boolean[blockSizeWithBorder];
                System.arraycopy(cells[blockSize], 1, cellsToCopy, 1, blockSizeWithBorder - 1);
                var result = neighbourMap.setTopBorder(cellsToCopy);
                return hasTrueValue(result);
            }
            case BOTTOM_RIGHT -> {
                boolean cell = cells[blockSize][blockSize];
                neighbourMap.setTopLeftCorner(cell);
                return cell;
            }
        }
        return false;
    }

    private boolean hasTrueValue(boolean[] cells) {
        for (var cell : cells) {
            if (cell) return true;
        }
        return false;
    }

    private Pair<boolean[], Boolean> getColumnCells(boolean[][] cells, int srcCol) {
        boolean[] cellsToCopy = new boolean[cells.length - 2];
        boolean hasTrue = false;
        for (int i = 1; i < cells.length - 1; i++) {
            cellsToCopy[i - 1] = cells[i][srcCol];
            if (cells[i][srcCol]) {
                hasTrue = true;
            }
        }
        return Pair.of(cellsToCopy, hasTrue);
    }

}