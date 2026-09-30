package slobben.cells.dto.incoming;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

public record ClientUpdateRequest(UUID client, String[] blocksToRemove, String[] blocksToAdd,
                                  @Nullable Integer blockLevel) {
}
