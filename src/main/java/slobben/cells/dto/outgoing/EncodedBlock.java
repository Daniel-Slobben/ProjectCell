package slobben.cells.dto.outgoing;

public record EncodedBlock(int x, int y, int generation, String encodedCells, String type) {

    public EncodedBlock copy() {
        return new EncodedBlock(x, y, generation, encodedCells, type);
    }
}
