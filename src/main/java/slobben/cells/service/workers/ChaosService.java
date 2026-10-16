package slobben.cells.service.workers;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import slobben.cells.dto.internal.Coordinates;
import slobben.cells.entities.Block;
import slobben.cells.entities.ChaosHit;
import slobben.cells.enums.ChaosType;
import slobben.cells.service.workers.chaosmakers.Maker;

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
    @Value("${cells.chaos.type:NULL}")
    private String preferredType = null;
    @Value("${cells.size.blockSize}")
    private int blockSize = 500;
    @Value("${cells.size.sectorSize}")
    private int sectorSize = 40;

    @PostConstruct
    void init() {
        createChaos();
    }

    public String getName() {
        return "ChaosService";
    }

    public void execute() {
        for (ChaosHit hit : chaosHits) {
            boolean doReset = hit.incrementAge();
            if (doReset) {
                clearChaosHit(hit);
                createChaos();
            }

            setNewCurrentViewpoint(hit);
        }
    }

    private void setNewCurrentViewpoint(ChaosHit hit) {
        short highestCount = 0;
        Coordinates highestCountCoordinates = null;

        for (Block block : blocks.values().stream()
                .filter(block -> block.getResponsibleChaosHit().equals(hit.getId()))
                .toList()) {
            short[][] sectorCount = block.getSectorCounts();

            for (int sx = 0; sx < sectorCount.length; sx++) {
                for (int sy = 0; sy < sectorCount.length; sy++) {
                    short count = sectorCount[sx][sy];

                    // filters sectors with just pure lines
                    if (count % sectorSize != 0 && count > highestCount) {
                        highestCount = count;
                        int worldX = block.getX() * blockSize + sx * sectorSize;
                        int worldY = block.getY() * blockSize + sy * sectorSize;
                        highestCountCoordinates = new Coordinates(worldX, worldY);
                    }
                }
            }
        }
        hit.setCurrentStartingView(highestCountCoordinates);
    }

    private void createChaos() {
        ChaosType type;
        try {
            type = ChaosType.valueOf(this.preferredType);
        } catch (IllegalArgumentException _) {
            type = getWeightedRandomType();
        }

        Maker maker = beanFactory.getBean(type.maker);
        ChaosHit chaosHit = maker.getChaosHit(0, 0);

        chaosHits.addFirst(chaosHit);
    }

    private void clearChaosHit(ChaosHit chaosHit) {
        List<String> keysToRemove = blocks.entrySet().stream().filter(entrySet -> chaosHit.getId().equals(entrySet.getValue().getResponsibleChaosHit())).map(Map.Entry::getKey).toList();
        keysToRemove.forEach(blocks::remove);

        log.info("Cleaned ChaosHit {} with age {}, total blocks: {}", chaosHit.getId(), chaosHit.getAge(), keysToRemove.size());
    }

    private ChaosType getWeightedRandomType() {
        ChaosType type = switch (random.nextInt(0, 6)) {
            case 0, 1 -> ChaosType.SQUARE;
            case 2, 3 -> ChaosType.DIAGONAL_LINES;
            case 4, 5 -> ChaosType.GROWTH_PATTERN;
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
