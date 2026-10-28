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
public class DiagonalMaker implements Maker {

    private static final Random random = new Random();

    private final WorldEditor worldEditor;

    @Override
    public ChaosHit getChaosHit(int worldX, int worldY, int size) {
        UUID chaosHitId = UUID.randomUUID();
        final int startX = (-(size / 2)) + worldX;
        final int startY = (-(size / 2)) + worldY;

        setCells(startX, startY, size, chaosHitId);

        return new ChaosHit("Diagonal cross " + size + " pixels wide", chaosHitId, new Coordinates(worldX, worldY), size / 3, size);
    }

    private void setCells(int startX, int startY, int squareSize, UUID hitId) {
        for (int i = 0; i < squareSize; i++) {
            worldEditor.setCell(startX + i, startY + i, hitId);
            worldEditor.setCell(startX + squareSize - 1 - i, startY + i, hitId);
        }
    }
}
