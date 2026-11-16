package slobben.cells.dto.incoming;

// sectors: the client wants the alive cells per sector of its blocks instead of the cells
public record ClientUpdateRequest(String[] blocksToRemove, String[] blocksToAdd, Boolean sectors) {

    // null from a frontend from before sectors
    public boolean watchesSectors() {
        return Boolean.TRUE.equals(sectors);
    }
}
