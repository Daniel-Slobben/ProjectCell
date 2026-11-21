package slobben.cells.service.workers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import slobben.cells.entities.Block;
import slobben.cells.entities.ChaosHit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
class BlockLoader {

    @Bean
    Map<String, Block> newBlocks() {
        return new ConcurrentHashMap<>();
    }

    @Bean
    Map<String, Block> blocks() {
        return new HashMap<>();
    }

    @Bean
    public List<ChaosHit> chaosHits() {
        return new ArrayList<>();
    }

    @Bean
    Random chaosRandom(@Value("${cells.chaos.seed:#{null}}") Long seed) {
        return seed == null ? new Random() : new Random(seed);
    }

}