package slobben.Cells.config;

import lombok.Builder;

@Builder
public record BlockUpdate(int x, int y, boolean[][] state) {
}
