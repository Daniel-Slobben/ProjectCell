package slobben.cells.dto.outgoing;

import java.util.Arrays;

public record EncodedBlock(int x, int y, int generation, EncodedBlockType type, byte[] encodedCells) {

    @Override
    public boolean equals(Object o) {
        return o instanceof EncodedBlock(
                int otherX, int otherY, int otherGeneration,
                EncodedBlockType otherType, byte[] otherCells
        )
                && x == otherX && y == otherY && generation == otherGeneration
                && type == otherType && Arrays.equals(encodedCells, otherCells);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * (31 * (31 * x + y) + generation) + type.hashCode()) + Arrays.hashCode(encodedCells);
    }
}
