package slobben.cells.entities.model;

import lombok.*;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import slobben.cells.dto.EncodedBlock;
import slobben.cells.dto.EncodedBlockBorders;
import slobben.cells.enums.BlockState;
import slobben.cells.util.BlockUtils;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@RequiredArgsConstructor
@AllArgsConstructor
@Builder
public class Block {
    private final int x;
    private final int y;
    @Builder.Default
    private int generation = 0;
    private boolean[][] cells;

    private boolean ghostBlock = false;
    private UUID responsibleChaosHit;

    private EncodedBlock encodedBlock;
    private EncodedBlockBorders encodedBlockBorders;

    private BlockState blockState = BlockState.ACTIVE;
    private List<boolean[][]> recordings = new ArrayList<>();
    private int recordingIndex = 0;

    public Block(int x, int y, UUID responsibleChaosHit, int blockSize) {
        this.x = x;
        this.y = y;
        this.responsibleChaosHit = responsibleChaosHit;
        this.cells = new boolean[blockSize + 2][blockSize + 2];
    }

    public Block(int x, int y, UUID responsibleChaosHit, boolean[][] cells) {
        this.x = x;
        this.y = y;
        this.responsibleChaosHit = responsibleChaosHit;
        this.cells = cells;
    }

    private static void setBit(byte[] packed, int i) {
        packed[i / 8] |= (byte) (1 << (i % 8));
    }

    @Override
    public int hashCode() {
        return getKey().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Block block && block.getKey().equals(this.getKey());
    }

    public synchronized EncodedBlock getEncodedBlock() {
        if (encodedBlock == null) {
            byte[] packed = getPacked(false);
            LZ4Compressor compressor = LZ4Factory.fastestInstance().fastCompressor();
            byte[] compressed = compressor.compress(packed);

            this.encodedBlock = new EncodedBlock(x, y, generation, Base64.getEncoder().encodeToString(compressed));
        }
        return new EncodedBlock(encodedBlock.x(), encodedBlock.y(), encodedBlock.generation(), encodedBlock.encodedCells());
    }

    public synchronized EncodedBlockBorders getEncodedBlockBorders() {
        if (encodedBlockBorders == null) {
            byte[] packed = getBorderPacked();
            LZ4Compressor compressor = LZ4Factory.fastestInstance().fastCompressor();
            byte[] compressed = compressor.compress(packed);

            this.encodedBlockBorders = new EncodedBlockBorders(x, y, generation, Base64.getEncoder().encodeToString(compressed));
        }
        return new EncodedBlockBorders(encodedBlockBorders.x(), encodedBlockBorders.y(), encodedBlockBorders.generation(), encodedBlockBorders.encodedCells());
    }

    public void clearEncodedBlock() {
        this.encodedBlock = null;
        this.encodedBlockBorders = null;
    }

    public void setNextHibernationState() {
        recordingIndex++;
        if (recordingIndex >= recordings.size()) {
            recordingIndex = 0;
        }
        cells = recordings.get(recordingIndex);
    }

    public String getKey() {
        return BlockUtils.getKey(x, y);
    }

    private byte[] getPacked(boolean fullBlock) {
        final int size = fullBlock ? cells.length : cells.length - 2;
        final int totalBits = size * size;
        final int startingIndex = fullBlock ? 0 : 1;
        byte[] packed = new byte[(totalBits + 7) / 8];

        for (int xrow = startingIndex; xrow < cells.length - startingIndex; xrow++) {
            for (int ycol = startingIndex; ycol < cells.length - startingIndex; ycol++) {
                int i = (xrow - startingIndex) * size + (ycol - startingIndex);
                if (cells[xrow][ycol]) {
                    setBit(packed, i);
                }
            }
        }
        return packed;
    }

    private byte[] getBorderPacked() {
        final int min = 1;
        final int max = cells.length - 2;
        final int size = max - min + 1;
        final int totalBits = size * 4 - 4;

        int index = 0;

        byte[] packed = new byte[(totalBits + 7) / 8];

        for (int i = min; i <= max; i++) {
            if (cells[i][max]) setBit(packed, index);
            index++;
        }
        for (int i = min; i <= max; i++) {
            if (cells[i][min]) setBit(packed, index);
            index++;
        }
        for (int i = min + 1; i < max; i++) {
            if (cells[min][i]) setBit(packed, index);
            index++;
        }
        for (int i = min + 1; i < max; i++) {
            if (cells[max][i]) setBit(packed, index);
            index++;
        }
        if (index != totalBits) {
            throw new IllegalStateException(
                    "index=" + index + " totalBits=" + totalBits + " size=" + size + " cells=" + cells.length);
        }
        return packed;
    }

}
