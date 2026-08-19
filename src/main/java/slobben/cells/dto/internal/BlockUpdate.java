package slobben.cells.dto.internal;

import lombok.Builder;
import slobben.cells.util.BlockUtils;

import java.util.UUID;

@Builder
public record BlockUpdate(
        int x,
        int y,
        boolean[][] state,
        UUID responsibleChaosHit) {

    public String getKey() {
        return BlockUtils.getKey(x, y);
    }
}
