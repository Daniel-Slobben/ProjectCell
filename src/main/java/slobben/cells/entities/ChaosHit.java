package slobben.cells.entities;

import lombok.Getter;
import lombok.Setter;
import slobben.cells.dto.internal.Coordinates;
import slobben.cells.dto.outgoing.ChaosHitDto;
import slobben.cells.enums.ChaosType;

import java.util.UUID;

@Getter
public class ChaosHit {
    // a hit with this max age lasts until the world resets
    public static final int NO_AGE = Integer.MAX_VALUE;

    private final UUID id;
    private final String name;
    @Setter
    private Coordinates currentStartingView;
    private final int maxAge;
    private final int size;
    private int age = 0;
    @Setter
    private ChaosType type;

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

    public boolean ages() {
        return maxAge != NO_AGE;
    }

    public boolean incrementAge() {
        age++;
        return age > maxAge;
    }

    public ChaosHitDto getDto() {
        return new ChaosHitDto(id, currentStartingView.x(), currentStartingView.y(), name, age);
    }
}
