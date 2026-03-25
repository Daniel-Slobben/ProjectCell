package slobben.Cells.service;

import com.mongodb.assertions.Assertions;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.Setter;
import slobben.Cells.entities.model.Block;

import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class InitializerService {
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

    private static Stream<Block> getBlockStream() {
        Assertions.assertTrue(blockAmount > 0);
        Assertions.assertTrue(blockSize > 0);
        Assertions.assertTrue(blockSizeWithBorder > 0);

        return IntStream.range(0, blockAmount * blockAmount).mapToObj(count -> {
            int x = count / blockAmount;
            int y = count % blockAmount;
            return new Block(x, y, new boolean[blockSizeWithBorder][blockSizeWithBorder]);
        });
    }

    public static Set<Block> getEmptyMap() {
        return getBlockStream().collect(Collectors.toSet());
    }

    public static Set<Block> getRandomMap() {
        Assertions.assertTrue(blockPopulation > 0);
        Assertions.assertTrue(cellPopulation > 0);

        return getBlockStream()
                .map(InitializerService::setBlockToRandom)
                .collect(Collectors.toSet());
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