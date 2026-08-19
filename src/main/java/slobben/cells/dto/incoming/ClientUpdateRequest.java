package slobben.cells.dto.incoming;

import java.util.UUID;

public record ClientUpdateRequest(UUID client, String[] blocksToRemove, String[] blocksToAdd) {
}
