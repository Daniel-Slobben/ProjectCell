package slobben.Cells.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import slobben.Cells.entities.model.Block;
import slobben.Cells.entities.model.Cell;
import slobben.Cells.entities.repository.BlockRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Getter
@Slf4j
@RequiredArgsConstructor
public class InitializerService {

    private final BlockRepository blockRepository;
    private final StitchingService stitchingService;
    private final MongoTemplate mongoTemplate;
    private final EnvironmentService environmentService;

    @SneakyThrows
    public ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, Block>> initializeMap() {
        log.info("Initializing {} map!", environmentService.getSetupMode());
        String setup = environmentService.getSetupMode();
        int blockAmount = environmentService.getBlockAmount();
        int blockSize = environmentService.getBlockSize();

        mongoTemplate.dropCollection(Block.class);
        ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, Block>> blocks = new ConcurrentHashMap<>(blockAmount);

        long totalTimerSetup = System.currentTimeMillis();
        for (int blockX = 0; blockX < blockAmount; blockX++) {
            for (int blockY = 0; blockY < blockAmount; blockY++) {
                Block block = new Block(blockX, blockY);

                Random random = new Random();
                if (setup.equals("RANDOM") && random.nextInt(0, environmentService.getBlockPopulation()) == 0) {
                    Map<Integer, Map<Integer, Cell>> cells = new HashMap<>();
                    for (int x = 0; x < blockSize; x++) {
                        for (int y = 0; y < blockSize; y++) {
                            if (random.nextInt(0, environmentService.getCellPopulation()) == 0) {
                                Cell cell = new Cell(x + (blockSize * blockX), y + (blockSize * blockY));
                                cells.computeIfAbsent(x + 1, row -> new HashMap<>()).put(y + 1, cell);
                            }
                        }
                    }
                    block.setCells(cells);
                    stitchingService.initializeStitch(block);
                    stitchingService.addBorderCells(block);
                } else {
                    block.setCells(new HashMap<>());
                }
                blocks.computeIfAbsent(block.getX(), row -> new ConcurrentHashMap<>()).put(block.getY(), block);
                log.info("X: {}", blockX);
            }
        }
        System.out.println("Time taken to generate: " + (System.currentTimeMillis() - totalTimerSetup));
        return blocks;
    }
}