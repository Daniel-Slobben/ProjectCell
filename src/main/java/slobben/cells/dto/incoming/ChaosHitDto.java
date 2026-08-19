package slobben.cells.dto.incoming;

import java.util.UUID;

public record ChaosHitDto(UUID id, int worldX, int worldY, String name, int age) {
}
