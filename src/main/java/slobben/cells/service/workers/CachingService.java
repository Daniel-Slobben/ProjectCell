package slobben.cells.service.workers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import slobben.cells.entities.model.Block;
import slobben.cells.service.workers.chaos.ChaosHit;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CachingService implements Worker {
    private static final int HITS_TO_CACHE = 10;
    private final List<ChaosHit> chaosHits;
    private final Map<String, Block> blocks;

    @Override
    public void execute() {
        blocks.values().stream().parallel().forEach(Block::clearEncodedBlock);

        List<UUID> hitIdCacheList = chaosHits.stream()
                .limit(HITS_TO_CACHE)
                .map(ChaosHit::getId)
                .toList();

        Set<Block> blocksToCache = blocks.values().stream()
                .filter(block -> hitIdCacheList.contains(block.getResponsibleChaosHit()))
                .collect(Collectors.toSet());

        blocksToCache.stream().parallel().forEach(Block::getEncodedBlock);
    }

    @Override
    public String getName() {
        return "Caching Encoded Blocks";
    }
}
