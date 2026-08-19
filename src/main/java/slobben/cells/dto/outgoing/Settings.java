package slobben.cells.dto.outgoing;

import slobben.cells.dto.incoming.ChaosHitDto;

import java.util.UUID;

public record Settings(int blockSize, UUID clientId, ChaosHitDto chaosHit) {
}
