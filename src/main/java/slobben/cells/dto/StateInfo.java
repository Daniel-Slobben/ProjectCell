package slobben.cells.dto;

import lombok.Builder;

@Builder
public record StateInfo(int blocksInMemory, int blocksUpdating) {
}
