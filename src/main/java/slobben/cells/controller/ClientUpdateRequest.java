package slobben.cells.controller;

import java.util.UUID;

public record ClientUpdateRequest(UUID client, String[] blocksToRemove, String[] blocksToAdd) {
}
