package slobben.cells.service.workers.chaosmakers;

import slobben.cells.entities.ChaosHit;

public interface Maker {
    ChaosHit getChaosHit(int worldX, int worldY);
}
