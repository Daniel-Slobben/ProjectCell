package slobben.cells.service.workers.chaos;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Service;
import slobben.cells.entities.model.Block;
import slobben.cells.service.workers.Worker;
import slobben.cells.service.workers.chaos.makers.Maker;

import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChaosService implements Worker {
    private final Map<String, Block> blocks;
    private final BeanFactory beanFactory;
    private final List<ChaosHit> chaosHits;

    private final Random random = new Random();

    private ChaosType lastType;

    @PostConstruct
    void init() {
        createChaos();
    }

    public String getName() {
        return "ChaosService";
    }

    public void execute() {
        ChaosHit currentHit = chaosHits.getFirst();
        boolean doReset = currentHit.incrementAge();
        if (doReset) {
            clearChaosHit(currentHit);
            createChaos();
        }
    }

    private void createChaos() {
        ChaosType type = getWeightedRandomType();

        Maker maker = beanFactory.getBean(type.maker);
        ChaosHit chaosHit = maker.getChaosHit();

        chaosHits.addFirst(chaosHit);
    }

    private void clearChaosHit(ChaosHit chaosHit) {
        List<String> keysToRemove = blocks.entrySet().stream().filter(entrySet -> chaosHit.getId().equals(entrySet.getValue().getResponsibleChaosHit())).map(Map.Entry::getKey).toList();
        keysToRemove.forEach(blocks::remove);
        log.info("Cleaned ChaosHit {} with age {}, total blocks: {}", chaosHit.getId(), chaosHit.getAge(), keysToRemove.size());
    }

    private ChaosType getWeightedRandomType() {
        ChaosType type = switch (random.nextInt(0, 4)) {
            case 0, 1 -> ChaosType.SQUARE;
            case 2, 3 -> ChaosType.DIAGONAL_LINES;
            default -> throw new IllegalStateException("Unexpected value: " + random.nextInt(0, 10));
        };

        // don't generate the same type twice in a row
        if (type == lastType) {
            return getWeightedRandomType();
        }
        this.lastType = type;
        return type;
    }

    public @NonNull ChaosHit getLatestHit() {
        return chaosHits.getFirst();
    }
}
