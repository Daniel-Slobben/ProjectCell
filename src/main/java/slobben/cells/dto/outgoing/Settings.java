package slobben.cells.dto.outgoing;

import java.util.UUID;

public record Settings(int blockSize, UUID clientId, ChaosHitDto chaosHit) {
}
