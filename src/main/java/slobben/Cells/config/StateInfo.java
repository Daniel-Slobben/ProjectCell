package slobben.Cells.config;

import lombok.Builder;

@Builder
public record StateInfo(int blocksInMemory, int blocksUpdating) {
}
