package slobben.Cells.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import slobben.Cells.entities.model.Block;
import slobben.Cells.entities.repository.BlockRepository;

import java.util.ArrayList;
import java.util.Random;

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
    public ArrayList<Block> initializeMap() {
        log.info("Initializing {} map!", environmentService.getSetupMode());
        String setup = environmentService.getSetupMode();
        int blockAmount = environmentService.getBlockAmount();
        int blockSize = environmentService.getBlockSize();
        int blockSizeWithBorder = environmentService.getBlockSizeWithBorder();

        mongoTemplate.dropCollection(Block.class);
        ArrayList<Block> blocks = new ArrayList<>(blockAmount * blockAmount);

        long totalTimerSetup = System.currentTimeMillis();
        for (int blockX = 0; blockX < blockAmount; blockX++) {
            for (int blockY = 0; blockY < blockAmount; blockY++) {
                Block block = new Block(blockX, blockY, new boolean[blockSizeWithBorder][blockSizeWithBorder]);

                Random random = new Random();
                if (setup.equals("RANDOM") && random.nextInt(0, environmentService.getBlockPopulation()) == 0) {
                    for (int x = 0; x < blockSize; x++) {
                        for (int y = 0; y < blockSize; y++) {
                            if (random.nextInt(0, environmentService.getCellPopulation()) == 0) {
                                block.getCells()[x][y] = true;
                            }
                        }
                    }
                    stitchingService.initializeStitch(block);
                    stitchingService.addBorderCells(block);
                }
                blocks.add(block);
            }
        }
        System.out.println("Time taken to generate: " + (System.currentTimeMillis() - totalTimerSetup));
        return blocks;
    }
}