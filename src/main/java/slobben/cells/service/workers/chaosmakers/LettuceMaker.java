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
public class LettuceMaker implements Maker {
    private static final int MIN_AMOUNT_OF_LINES = 4;
    private static final int MAX_AMOUNT_OF_LINES = 12;
    private static final int LINE_WIDTH = 2;

    private static final Random random = new Random();

    private final WorldEditor worldEditor;

    @Override
    public ChaosHit getChaosHit(int worldX, int worldY, int size) {
        return getChaosHit(worldX, worldY, size, random.nextInt(MIN_AMOUNT_OF_LINES, MAX_AMOUNT_OF_LINES + 1));
    }

    public ChaosHit getChaosHit(int worldX, int worldY, int size, int amountOfLines) {
        UUID chaosHitId = UUID.randomUUID();

        final int startX = worldX - size / 2;
        final int startY = worldY - size / 2;

        setCells(startX, startY, size, amountOfLines, chaosHitId);

        return new ChaosHit("Lettuce grid of " + amountOfLines + " lines " + size + " pixels wide", chaosHitId, new Coordinates(worldX, worldY), size / 2 + 1000, size);
    }

    private void setCells(int startX, int startY, int size, int amountOfLines, UUID hitId) {
        int span = size - LINE_WIDTH;
        for (int i = 0; i < amountOfLines; i++) {
            // computed per line so rounding is spread out and the last line lands exactly on the high edge
            int offset = i * span / (amountOfLines - 1);
            for (int j = 0; j < size; j++) {
                for (int line = 0; line < LINE_WIDTH; line++) {
                    // verticals
                    worldEditor.setCell(startX + offset + line, startY + j, hitId);
                    // horizontals
                    worldEditor.setCell(startX + j, startY + offset + line, hitId);
                }
            }
        }
    }
}
