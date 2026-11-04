package slobben.cells.service.workers;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import slobben.cells.dto.internal.Coordinates;
import slobben.cells.entities.Block;
import slobben.cells.enums.BlockState;
import slobben.cells.enums.Direction;
import slobben.cells.service.ExecutorService;
import slobben.cells.util.BlockUtils;
import slobben.cells.util.PackedRows;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static slobben.cells.util.BlockUtils.getKey;

@RequiredArgsConstructor
@Service
public class Stitching extends Worker {
    private final ExecutorService executorService;
    private final Map<String, Block> blocks;
    private final Map<String, Block> newBlocks;
    @Value("${cells.size.blockSize}")
    private int blockSize;
    private long[] innerMask;

    @PostConstruct
    void init() {
        innerMask = PackedRows.innerMask(blockSize);
    }

    @Override
    public String getName() {
        return "Adding bordercells";
    }

    public void execute() {
        blocks.values().parallelStream().forEach(this::prepareBorders);

        Set<Runnable> tasks = blocks.values().stream().map(block -> (Runnable) () -> addBorderCells(block)).collect(Collectors.toSet());
        executorService.executeTasksParallel(tasks, getName());
    }

    // the borders are copied from the packed rows, so NEW blocks are packed from their cells first
    private void prepareBorders(Block block) {
        if (block.getBlockState() == BlockState.NEW) {
            block.setPackedCells(PackedRows.pack(block.getCells()));
        }
        clearBlockBorders(block.getPackedCells());
    }

    private void clearBlockBorders(long[][] packed) {
        int max = blockSize + 1;
        Arrays.fill(packed[0], 0L);
        Arrays.fill(packed[max], 0L);
        for (int x = 1; x < max; x++) {
            PackedRows.clear(packed[x], 0);
            PackedRows.clear(packed[x], max);
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
        long[][] packed = block.getPackedCells();
        UUID hit = block.getResponsibleChaosHit();
        int max = blockSize + 1;
        switch (direction) {
            case LOW_X_LOW_Y -> copyCell(key, hit, packed[1], 1, max, max);
            case LOW_X_MID_Y -> copyRow(key, hit, packed[1], max);
            case LOW_X_HIGH_Y -> copyCell(key, hit, packed[1], blockSize, max, 0);
            case MID_X_LOW_Y -> copyColumn(key, hit, packed, 1, max);
            case MID_X_HIGH_Y -> copyColumn(key, hit, packed, blockSize, 0);
            case HIGH_X_LOW_Y -> copyCell(key, hit, packed[blockSize], 1, 0, max);
            case HIGH_X_MID_Y -> copyRow(key, hit, packed[blockSize], 0);
            case HIGH_X_HIGH_Y -> copyCell(key, hit, packed[blockSize], blockSize, 0, 0);
        }
    }

    private void copyCell(String key, UUID hit, long[] srcRow, int srcCol, int dstRow, int dstCol) {
        if (PackedRows.get(srcRow, srcCol)) {
            operateOnBlock(key, hit, (Block b) -> PackedRows.set(b.getPackedCells()[dstRow], dstCol));
        }
    }

    private void copyRow(String key, UUID hit, long[] srcRow, int dstRow) {
        long any = 0;
        for (int i = 0; i < srcRow.length; i++) {
            any |= srcRow[i] & innerMask[i];
        }
        if (any == 0) return;

        operateOnBlock(key, hit, (Block b) -> {
            long[] dst = b.getPackedCells()[dstRow];
            for (int i = 0; i < dst.length; i++) {
                dst[i] |= srcRow[i] & innerMask[i];
            }
        });
    }

    private void copyColumn(String key, UUID hit, long[][] packed, int srcCol, int dstCol) {
        boolean any = false;
        for (int x = 1; x <= blockSize && !any; x++) {
            any = PackedRows.get(packed[x], srcCol);
        }
        if (!any) return;

        operateOnBlock(key, hit, (Block b) -> {
            long[][] dst = b.getPackedCells();
            for (int x = 1; x <= blockSize; x++) {
                if (PackedRows.get(packed[x], srcCol)) PackedRows.set(dst[x], dstCol);
            }
        });
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