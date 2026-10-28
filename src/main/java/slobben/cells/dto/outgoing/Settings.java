package slobben.cells.dto.outgoing;

import slobben.cells.dto.internal.Coordinates;

import java.util.List;
import java.util.UUID;

public record Settings(int blockSize, UUID clientId, Coordinates viewport, List<EncodedBlock> blocks) {
}
