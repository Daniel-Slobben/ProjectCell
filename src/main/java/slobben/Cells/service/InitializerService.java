package slobben.Cells.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import slobben.Cells.database.model.Block;
import slobben.Cells.database.model.Cell;
import slobben.Cells.database.repository.BlockRepository;

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
        String setup = environmentService.getSetupMode();
        int blockAmount = environmentService.getBlockAmount();
        int blockSize = environmentService.getBlockSize();

        mongoTemplate.dropCollection(Block.class);

        stitchingService.initializeStich();
        ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, Block>> blocks = new ConcurrentHashMap<>(blockAmount);

        long totalTimerSetup = System.currentTimeMillis();
        for (int blockX = 0; blockX < blockAmount; blockX++) {
            for (int blockY = 0; blockY < blockAmount; blockY++) {
                int finalBlockX = blockX;
                int finalBlockY = blockY;
                Block block = new Block(finalBlockX, finalBlockY, 0);

                Random random = new Random();
                if ((setup.equals("SPARSE") && random.nextInt(0, environmentService.getSparseAmount()) == 0) || setup.equals("RANDOM")) {
                    Map<Integer, Map<Integer, Cell>> cells = new HashMap<>();
                    for (int x = 0; x < blockSize; x++) {
                        for (int y = 0; y < blockSize; y++) {
                            if (random.nextInt(0, 6) == 0) {
                                Cell cell = new Cell(x + (blockSize * finalBlockX), y + (blockSize * finalBlockY));
                                cells.computeIfAbsent(x + 1, row -> new HashMap<>()).put(y + 1, cell);
                            }
                        }
                    }
                    block.setCells(cells);
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
