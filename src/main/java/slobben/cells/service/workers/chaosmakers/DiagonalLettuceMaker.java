package slobben.cells.service.workers.chaosmakers;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import slobben.cells.dto.internal.Coordinates;
import slobben.cells.entities.ChaosHit;
import slobben.cells.service.WorldEditor;

import java.util.Random;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DiagonalLettuceMaker implements Maker {

    private final WorldEditor worldEditor;
    private final Random random;

    @Override
    public ChaosHit getChaosHit(int worldX, int worldY, int size) {
        UUID chaosHitId = UUID.randomUUID();
        final int startX = worldX - size / 2;
        final int startY = worldY - size / 2;

        int hitsHize = random.nextInt(2, 10);
        int distanceBetweenCrosses = size / hitsHize;

        for (int i = 0; i < hitsHize; i++) {
            for (int j = 0; j < hitsHize; j++) {
                setCells(startX + i * distanceBetweenCrosses + size / hitsHize, startY + j * distanceBetweenCrosses + size / hitsHize, size / hitsHize, chaosHitId);
            }
        }

        return new ChaosHit("Diagonal cross " + size + " pixels wide", chaosHitId, new Coordinates(worldX, worldY), size / 3);
    }

    private void setCells(int startX, int startY, int squareSize, UUID hitId) {
        for (int i = 0; i < squareSize; i++) {
            worldEditor.setCell(startX + i, startY + i, hitId);
            worldEditor.setCell(startX + squareSize - 1 - i, startY + i, hitId);
        }
    }
}
