package slobben.cells.entities.model;

import lombok.*;
import slobben.cells.controller.EncodedBlock;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.BitSet;

import static slobben.cells.util.Compress.gzip;

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

    public Block(int x, int y, boolean[][] cells) {
        this.x = x;
        this.y = y;
        this.cells = cells;
    }

    @SneakyThrows
    public EncodedBlock getEncodedBlock() {
        final int blockSize = cells.length;
        BitSet bitSet = new BitSet((blockSize - 2) * (blockSize - 2));
        for (int xrow = 1; xrow < cells.length - 1; xrow++) {
            for (int ycol = 1; ycol < cells.length - 1; ycol++) {
                bitSet.set((blockSize - 2) * (xrow - 1) + (ycol - 1), cells[xrow][ycol]);
            }
        }
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        gzip(new ByteArrayInputStream(bitSet.toByteArray()), os);
        return new EncodedBlock(x, y, Base64.getEncoder().encodeToString(os.toByteArray()));
    }
}
