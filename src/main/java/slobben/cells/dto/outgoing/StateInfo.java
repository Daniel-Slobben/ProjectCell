package slobben.cells.dto.outgoing;

import lombok.Builder;

@Builder
public record StateInfo(int blocksInMemory, int blocksUpdating) {
}
