package slobben.cells.dto;

import lombok.Builder;
import slobben.cells.service.workers.chaos.ChaosHit;
import slobben.cells.util.BlockUtils;

@Builder
public record BlockUpdate(
        int x,
        int y,
        boolean[][] state,
        ChaosHit responsibleChaosHit) {

    public String getKey() {
        return BlockUtils.getKey(x, y);
    }
}
