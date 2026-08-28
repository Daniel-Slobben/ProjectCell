package slobben.cells.dto.outgoing;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

@NullMarked
public record ReconnectResponse(UUID clientId, @Nullable ChaosHitDto chaosHit) {
}
