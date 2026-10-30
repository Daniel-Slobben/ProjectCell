package slobben.cells.entities;

import lombok.Getter;
import lombok.Setter;
import slobben.cells.dto.internal.Coordinates;
import slobben.cells.dto.outgoing.ChaosHitDto;

import java.util.UUID;

@Getter
public class ChaosHit {
    private final UUID id;
    private final String name;
    @Setter
    private Coordinates currentStartingView;
    private final int maxAge;
    private final int size;
    private int age = 0;

    public ChaosHit(String name, UUID id, Coordinates currentStartingView, int maxAge, int size) {
        this.name = name;
        this.id = id;
        this.currentStartingView = currentStartingView;
        this.maxAge = maxAge;
        this.size = size;
    }

    public boolean isActive() {
        return age <= maxAge;
    }

    public boolean incrementAge() {
        age++;
        return age > maxAge;
    }

    public ChaosHitDto getDto() {
        return new ChaosHitDto(id, currentStartingView.x(), currentStartingView.y(), name, age);
    }
}
