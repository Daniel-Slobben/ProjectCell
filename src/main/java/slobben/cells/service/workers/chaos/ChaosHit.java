package slobben.cells.service.workers.chaos;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.util.Pair;
import slobben.cells.dto.outgoing.ChaosHitDto;

import java.util.UUID;
import java.util.function.IntFunction;

@RequiredArgsConstructor
@Getter
public class ChaosHit {
    private final UUID id = UUID.randomUUID();
    private final String name;
    private final IntFunction<Pair<Integer, Integer>> getActiveView;
    private final int maxAge;

    private int age;

    public boolean incrementAge() {
        age++;
        return age > maxAge;
    }

    public ChaosHitDto getDto() {
        Pair<Integer, Integer> currentStartingView = getActiveView.apply(age);
        return new ChaosHitDto(id, currentStartingView.getFirst(), currentStartingView.getSecond(), name, age);
    }
}
