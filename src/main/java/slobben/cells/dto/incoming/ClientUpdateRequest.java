package slobben.cells.dto.incoming;

public record ClientUpdateRequest(String[] blocksToRemove, String[] blocksToAdd) {
}
