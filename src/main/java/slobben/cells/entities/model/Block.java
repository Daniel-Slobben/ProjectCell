package slobben.cells.entities.model;

import lombok.*;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import slobben.cells.dto.EncodedBlock;
import slobben.cells.enums.BlockState;
import slobben.cells.util.BlockUtils;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Getter
@Setter
@RequiredArgsConstructor
@AllArgsConstructor
@Builder
public class Block {

    private final int x;
    private final int y;
    private int generation = 0;
    private boolean ghostBlock = false;
    private boolean[][] cells;

    private BlockState blockState = BlockState.ACTIVE;
    private List<boolean[][]> recordings = new ArrayList<>();
    private int recordingIndex = 0;

    @Override
    public int hashCode() {
        return getKey().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Block block && block.getKey().equals(this.getKey());
    }

    public Block(int x, int y, int blockSize) {
        this.x = x;
        this.y = y;
        this.cells = new boolean[blockSize + 2][blockSize + 2];
    }

    public Block(int x, int y, boolean[][] cells) {
        this.x = x;
        this.y = y;
        this.cells = cells;
    }

    public byte[] getByteValue() {
        return getPacked(true);
    }

    public EncodedBlock getEncodedBlock() {
        byte[] packed = getPacked(false);
        LZ4Compressor compressor = LZ4Factory.fastestInstance().fastCompressor();
        byte[] compressed = compressor.compress(packed);

        return new EncodedBlock(x, y, Base64.getEncoder().encodeToString(compressed));
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
                    packed[i / 8] |= (byte) (1 << (i % 8));
                }
            }
        }
        return packed;
    }
}
