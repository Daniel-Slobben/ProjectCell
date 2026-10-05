package slobben.cells.dto.incoming;

import java.util.List;
import java.util.UUID;

public record DeleteBlocksRequest(UUID client, List<String> blocksToDelete) {
}
