package slobben.cells.dto.outgoing;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import slobben.cells.dto.internal.Coordinates;

import java.util.UUID;

@NullMarked
public record ReconnectResponse(UUID clientId, @Nullable Coordinates viewport) {
}
