package slobben.cells.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import slobben.cells.dto.BlockUpdate;
import slobben.cells.entities.model.Block;
import slobben.cells.entities.model.BorderInfo;
import slobben.cells.enums.SetupMode;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Configuration
@RequiredArgsConstructor
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

    public Set<Block> getRandomMap() {
        return getBlockStream()
                .map(this::setBlockToRandom)
                .collect(Collectors.toSet());
    }

    @Bean
    public Map<String, Block> ghostBlocks() {
        return new HashMap<>();
    }

    @Bean
    public List<BlockUpdate> blockUpdates() {
        return new CopyOnWriteArrayList<>();
    }

    @Bean
    public Map<String, BorderInfo> bordersMap() {
        return new ConcurrentHashMap<>();
    }

    @Bean
    public Set<Block> blocks() {
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

    public Set<Block> getEmptyMap() {
        return getBlockStream().collect(Collectors.toSet());
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