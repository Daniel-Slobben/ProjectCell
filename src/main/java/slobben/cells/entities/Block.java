package slobben.cells.entities;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import slobben.cells.dto.outgoing.EncodedBlock;
import slobben.cells.enums.BlockState;
import slobben.cells.util.BlockUtils;
import slobben.cells.util.PackedRows;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

@Getter
@Setter
public class Block {
    private final int x;
    private final int y;
    private int generation = 0;
    // only while NEW, after that the packed cells are the state
    private boolean[][] cells;
    private short[][] sectorCounts;
    private long[][] packedCells;
    // outer columns of the packed cells: the edges (column 1 and size) from the GoL for Stitching, the borders
    // (column 0 and size + 1) from Stitching for the GoL
    private Columns edges;
    private Columns borders;
    private BlockState blockState = BlockState.NEW;

    private UUID responsibleChaosHit;

    // encodings are only sent for blocks a client watches, so the Packer compresses them on first use
    @Getter(AccessLevel.NONE)
    private Supplier<EncodedBlock> encodedBlock;
    @Getter(AccessLevel.NONE)
    private Supplier<EncodedBlock> encodedBlockBorders;

    private List<long[][]> recordings = new ArrayList<>();
    private int recordingIndex = 0;

    public Block(int x, int y, UUID responsibleChaosHit, int blockSize) {
        this.x = x;
        this.y = y;
        this.responsibleChaosHit = responsibleChaosHit;
        this.cells = new boolean[blockSize + 2][blockSize + 2];
        this.packedCells = PackedRows.create(blockSize);
    }

    @Override
    public int hashCode() {
        return getKey().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Block block && block.getKey().equals(this.getKey());
    }

    public void blockUpdated() {
        generation++;
        blockState = BlockState.ACTIVE;
        cells = null;
    }

    public EncodedBlock getEncodedBlock() {
        return encodedBlock == null ? null : encodedBlock.get();
    }

    public EncodedBlock getEncodedBlockBorders() {
        return encodedBlockBorders == null ? null : encodedBlockBorders.get();
    }

    public String getKey() {
        return BlockUtils.getKey(x, y);
    }

    public void setNextHibernationState() {
        recordingIndex++;
        if (recordingIndex >= recordings.size()) {
            recordingIndex = 0;
        }
        packedCells = recordings.get(recordingIndex);
        generation++;
    }

}
