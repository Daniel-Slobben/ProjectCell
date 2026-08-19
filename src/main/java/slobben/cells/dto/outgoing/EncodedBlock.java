package slobben.cells.dto.outgoing;

public record EncodedBlock(int x, int y, int generation, String encodedCells, String type) implements Cloneable {

    public EncodedBlock clone() {
        try {
            return (EncodedBlock) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }

    }

}
