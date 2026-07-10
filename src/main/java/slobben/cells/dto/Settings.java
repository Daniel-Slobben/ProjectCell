package slobben.cells.dto;

import slobben.cells.service.workers.chaos.ChaosHit;

import java.util.UUID;

public record Settings(int blockSize, UUID clientId, ChaosHit chaosHit) {
}
