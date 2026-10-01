package slobben.cells.service.workers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import slobben.cells.entities.model.Block;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CachingService implements Worker {
    private final Map<String, Block> blocks;

    @Override
    public void execute() {
        blocks.values().stream()
                .parallel()
                .forEach(Block::clearEncodedBlock);

        blocks.values().stream()
                .parallel()
                .forEach(Block::getEncodedBlock);
    }

    @Override
    public String getName() {
        return "Caching Encoded Blocks";
    }
}
