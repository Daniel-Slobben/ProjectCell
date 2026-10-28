package slobben.cells.service.workers.chaosmakers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import slobben.cells.dto.internal.Coordinates;
import slobben.cells.entities.ChaosHit;
import slobben.cells.service.WorldEditor;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class SquareMaker implements Maker {

    private final WorldEditor worldEditor;

    @Override
    public ChaosHit getChaosHit(int worldX, int worldY, int size) {
        UUID chaosHitId = UUID.randomUUID();

        final int startX = worldX - size / 2;
        final int startY = worldY - size / 2;

        setCells(startX, startY, chaosHitId, size);

        return new ChaosHit("2 pixels thick square " + size + " pixels wide", chaosHitId, new Coordinates(worldX, worldY), size / 2 + 1000, size);
    }

    private void setCells(int startX, int startY, UUID hitId, int size) {
        for (int i = 0; i < size; i++) {
            worldEditor.setCell(startX, startY + i, hitId);
            worldEditor.setCell(startX + 1, startY + i, hitId);

            worldEditor.setCell(startX + size - 1, startY + i, hitId);
            worldEditor.setCell(startX + size - 2, startY + i, hitId);

            worldEditor.setCell(startX + i, startY, hitId);
            worldEditor.setCell(startX + i, startY + 1, hitId);

            worldEditor.setCell(startX + i, startY + size - 1, hitId);
            worldEditor.setCell(startX + i, startY + size - 2, hitId);
        }
    }
}
