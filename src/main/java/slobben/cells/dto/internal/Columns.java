package slobben.cells.dto.internal;

import slobben.cells.entities.Block;

/**
 * Two columns of the cells of a block as bit sets over the rows (bit x for row x). The worker that knows them leaves
 * them on the block, so that the other one does not have to read every row. They go with one cell array in one
 * generation.
 */
public record Columns(long[][] cells, int generation, long[] left, long[] right) {

    public boolean of(Block block) {
        return cells == block.getPackedCells() && generation == block.getGeneration();
    }
}
