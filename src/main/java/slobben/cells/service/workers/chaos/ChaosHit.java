package slobben.cells.service.workers.chaos;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import slobben.cells.entities.Pattern;

import java.util.UUID;

@RequiredArgsConstructor
@Getter
public class ChaosHit {
    private final UUID id = UUID.randomUUID();
    private final int worldX;
    private final int worldY;
    private final String name;
    private final Pattern pattern;
    private int age;

    public void incrementAge() {
        age++;
    }
}
