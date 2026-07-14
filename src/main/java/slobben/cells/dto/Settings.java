package slobben.cells.dto;

import java.util.UUID;

public record Settings(int blockSize, UUID clientId, ChaosHitDto chaosHit) {
}
