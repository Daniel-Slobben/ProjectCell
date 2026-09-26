package slobben.cells.service.workers.chaos;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import slobben.cells.dto.outgoing.ChaosHitDto;
import slobben.cells.entities.Coordinates;

import java.util.UUID;
import java.util.function.IntFunction;

@RequiredArgsConstructor
@Getter
public class ChaosHit {
    private final UUID id = UUID.randomUUID();
    private final String name;
    private final IntFunction<Coordinates> getActiveView;
    private final int maxAge;

    private int age;

    public boolean incrementAge() {
        age++;
        return age > maxAge;
    }

    public ChaosHitDto getDto() {
        Coordinates currentStartingView = getActiveView.apply(age);
        return new ChaosHitDto(id, currentStartingView.x(), currentStartingView.x(), name, age);
    }
}
