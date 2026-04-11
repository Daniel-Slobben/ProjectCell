package slobben.cells.config;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import slobben.cells.entities.model.Block;
import slobben.cells.enums.SetupMode;
import slobben.cells.service.EnvironmentService;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Configuration
@RequiredArgsConstructor
public class BlockConfig {

    private final EnvironmentService environmentService;

    private static final Random random = new Random();
    @Setter
    private static int blockAmount;
    @Setter
    private static int blockSize;
    @Setter
    private static int blockSizeWithBorder;
    @Setter
    private static int blockPopulation;
    @Setter
    private static int cellPopulation;

    public static Set<Block> getRandomMap() {
        assert blockPopulation > 0;
        assert cellPopulation > 0;

        return getBlockStream()
                .map(BlockConfig::setBlockToRandom)
                .collect(Collectors.toSet());
    }

    @Bean
    public Map<String, Block> ghostBlocks() {
        return new HashMap<>();
    }

    private static Stream<Block> getBlockStream() {
        if (blockAmount == 0) {
            return Stream.empty();
        }
        assert blockSize > 0;
        assert blockSizeWithBorder > 0;

        return IntStream.range(0, blockAmount * blockAmount).mapToObj(count -> {
            int x = count / blockAmount;
            int y = count % blockAmount;
            return new Block(x, y, new boolean[blockSizeWithBorder][blockSizeWithBorder]);
        });
    }

    public static Set<Block> getEmptyMap() {
        return getBlockStream().collect(Collectors.toSet());
    }

    @Bean
    public Set<Block> blocks() {
        return switch (SetupMode.valueOf(environmentService.getSetupMode())) {
            case RANDOM -> BlockConfig.getRandomMap();
            case EMPTY -> BlockConfig.getEmptyMap();
            default ->
                    throw new IllegalStateException("SetupMode has an unexpected value: " + environmentService.getSetupMode());
        };
    }

    private static Block setBlockToRandom(Block block) {
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