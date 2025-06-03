package slobben.Cells.service;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import slobben.Cells.database.model.Block;
import slobben.Cells.database.model.Cell;
import slobben.Cells.database.repository.BlockRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
    @PostConstruct
    private void initializeMap() {
        int blockAmount = environmentService.getBlockAmount();
        int blockSize = environmentService.getBlockSize();

        mongoTemplate.dropCollection(Cell.class);
        mongoTemplate.dropCollection(Block.class);

        stitchingService.initializeStich();

        ExecutorService executor = Executors.newFixedThreadPool(24);
        long totalTimerSetup = System.currentTimeMillis();

        for (int blockX = 0; blockX < blockAmount; blockX++) {
            for (int blockY = 0; blockY < blockAmount; blockY++) {
                int finalBlockX = blockX;
                int finalBlockY = blockY;
                Block block = new Block(finalBlockX, finalBlockY, 0);
                executor.execute(() -> {
                    Random random = new Random();
                    Map<Integer, Map<Integer, Cell>> cells = new HashMap<>();
                    if (random.nextInt(0, 1) == 0) {
                        long addTimer = System.currentTimeMillis();
                        log.debug("Starting to generate Block X: {}, Y: {}", finalBlockY, finalBlockY);
                        for (int x = 0; x < blockSize; x++) {
                            for (int y = 0; y < blockSize; y++) {
                                if (random.nextInt(0, 6) == 0) {
                                    Cell cell = new Cell(x + (blockSize * finalBlockX), y + (blockSize * finalBlockY));
                                    cells.computeIfAbsent(x + 1, row -> new HashMap<>()).put(y + 1, cell);
                                }
                            }
                        }
                        log.debug("Generated Block X: {}, Y: {}, Time taken: {}ms", finalBlockY, finalBlockY, System.currentTimeMillis() - addTimer);
                    }
                    long saveTimer = System.currentTimeMillis();
                    log.debug("Starting to save Block X: {}, Y: {}", finalBlockY, finalBlockY);
                    block.setCells(cells);
                    stitchingService.addBorderCells(block);
                    blockRepository.save(block);
                    log.debug("Saved Block X: {}, Y: {}, Time taken: {}ms", finalBlockY, finalBlockY, System.currentTimeMillis() - saveTimer);
                });
            }
        }
        executor.close();
        executor.awaitTermination(120, TimeUnit.SECONDS);
        stitchingService.updateDatabase();
        System.out.println("Time taken to generate: " + (System.currentTimeMillis() - totalTimerSetup));
    }
}
