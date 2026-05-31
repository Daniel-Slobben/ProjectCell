package slobben.cells.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import slobben.cells.dto.BlockUpdate;
import slobben.cells.entities.model.Block;
import slobben.cells.entities.model.BorderInfo;
import slobben.cells.enums.SetupMode;
import slobben.cells.util.BlockUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class BlockConfig {

    private static final Random random = new Random();
    private final EnvironmentConfig environmentConfig;

    @Value("${cells.random.blockPopulation}")
    private int blockPopulation;
    @Value("${cells.random.cellPopulation}")
    private int cellPopulation;
    @Value("${cells.setup}")
    private String setupMode;
    @Value("${cells.size.blockSize}")
    private int blockSize;

    public Map<String, Block> getRandomMap() {
        return getBlockStream()
                .map(this::setBlockToRandom)
                .collect(Collectors.toMap(Block::getKey, block -> block));
    }

    @Bean
    public Map<String, Block> ghostBlocks() {
        return new HashMap<>();
    }

    @Bean
    public Map<String, BlockUpdate> blockUpdates() {
        return new ConcurrentHashMap<>();
    }

    @Bean
    public Map<String, BorderInfo> bordersMap() {
        return new ConcurrentHashMap<>();
    }

    @Bean
    public Map<String, Block> blocks() {
        return switch (SetupMode.valueOf(setupMode)) {
            case RANDOM -> getRandomMap();
            case EMPTY -> getEmptyMap();
        };
    }

    private Stream<Block> getBlockStream() {
        int blockAmount = environmentConfig.getBlockAmount();
        if (environmentConfig.getBlockAmount() == 0) {
            return Stream.empty();
        }

        return IntStream.range(0, blockAmount * blockAmount).mapToObj(count -> {
            int x = count / blockAmount;
            int y = count % blockAmount;
            return new Block(x, y, new boolean[environmentConfig.getBlockSizeWithBorder()][environmentConfig.getBlockSizeWithBorder()]);
        });
    }

    public Map<String, Block> getEmptyMap() {
        return getBlockStream().collect(Collectors.toMap((Block block) -> BlockUtils.getKey(block.getX(), block.getY()), (Block block) -> block));
    }

    private Block setBlockToRandom(Block block) {
        if (random.nextInt(0, blockPopulation) != 0) {
            return block;
        }
        for (int x = 0; x < blockSize; x++) {
            for (int y = 0; y < blockSize; y++) {
                if (random.nextInt(0, cellPopulation) == 0) {
                    block.getCells()[x][y] = true;
                }
            }
        }
        return block;
    }
}