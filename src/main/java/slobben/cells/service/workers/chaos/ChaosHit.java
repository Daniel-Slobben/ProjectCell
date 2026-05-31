package slobben.cells.service.workers.chaos;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import slobben.cells.entities.Pattern;

@RequiredArgsConstructor
@Getter
public class ChaosHit {
    private final int worldX;
    private final int worldY;
    private final String name;
    private final Pattern pattern;
    private int age;
}
