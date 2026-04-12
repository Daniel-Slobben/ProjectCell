package slobben.cells.entities.model;

import lombok.*;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import slobben.cells.dto.EncodedBlock;

import java.util.Base64;
import java.util.LinkedList;

@Getter
@Setter
@RequiredArgsConstructor
@AllArgsConstructor
@Builder
public class Block {

    private final int x;
    private final int y;
    private int generation = 0;
    private boolean isUpdatingWeb = false;
    private boolean ghostBlock = false;
    private boolean[][] cells;
    private LinkedList<byte[]> deltas = new LinkedList<>();

    public Block(int x, int y, boolean[][] cells) {
        this.x = x;
        this.y = y;
        this.cells = cells;
    }

    public void addByteArrayToDelta(byte[] delta) {
        deltas.add(delta);
        if (deltas.size() > 3) {
            deltas.pollFirst();
        }
    }

    public EncodedBlock getDeltaBlock() {
        return new EncodedBlock(x, y, Base64.getEncoder().encodeToString(deltas.peekLast()));
    }

    public void printDelta() {
        byte[] delta = deltas.peekLast();
        int pixelCounter = 0;
        for (int i = 0; i < delta.length; i++) {
        }
    }

    public EncodedBlock getEncodedBlock() {
        final int innerSize = cells.length - 2;
        final int totalBits = innerSize * innerSize;
        byte[] packed = new byte[(totalBits + 7) / 8];

        for (int xrow = 1; xrow < cells.length - 1; xrow++) {
            for (int ycol = 1; ycol < cells.length - 1; ycol++) {
                int i = (xrow - 1) * innerSize + (ycol - 1);
                if (cells[xrow][ycol]) {
                    packed[i / 8] |= (byte) (1 << (i % 8));
                }
            }
        }
        LZ4Compressor compressor = LZ4Factory.fastestInstance().fastCompressor();
        byte[] compressed = compressor.compress(packed);

        return new EncodedBlock(x, y, Base64.getEncoder().encodeToString(compressed));
    }
}
