package slobben.cells.dto.outgoing;

import java.util.List;
import java.util.UUID;

public record Settings(int blockSize, UUID clientId, ChaosHitDto chaosHit, List<EncodedBlock> blocks) {
}
