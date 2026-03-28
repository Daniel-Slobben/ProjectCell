package slobben.Cells.entities.model;

import lombok.*;

import java.util.Base64;
import java.util.BitSet;

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
    private final boolean[][] cells;

    public EncodedBlock getEncodedBlock() {
        BitSet bitSet = new BitSet(cells.length * cells[0].length);
        final int blockSize = cells.length;
        for (int x = 0; x < cells.length ; x++) {
            for (int y = 0; y < cells[0].length; y++) {
                bitSet.set(blockSize * x + y, cells[x][y]);
            }
        }
        return new EncodedBlock(x, y, Base64.getEncoder().encodeToString(bitSet.toByteArray()));
    }
}
