package slobben.cells.controller;

import lombok.Builder;

@Builder
public record StateInfo(int blocksInMemory, int blocksUpdating) {
}
