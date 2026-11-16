package slobben.cells.dto.incoming;

public record ClientUpdateRequest(String[] blocksToRemove, String[] blocksToAdd, Boolean sectors) {

    public boolean watchesSectors() {
        return Boolean.TRUE.equals(sectors);
    }
}
