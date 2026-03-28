package slobben.cells.config;

import lombok.Builder;

@Builder
public record BlockUpdate(int x, int y, boolean[][] state) {
}
