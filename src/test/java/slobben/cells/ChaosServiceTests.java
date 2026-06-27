package slobben.cells;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.util.Pair;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;
import slobben.cells.dto.BlockUpdate;
import slobben.cells.service.workers.chaos.ChaosService;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@SpringBootTest
@ExtendWith(SpringExtension.class)
@ComponentScan("slobben.cells.service")
@ActiveProfiles(profiles = "normal")
class ChaosServiceTests {

    @Autowired
    private ChaosService chaosService;
    @Autowired
    private Map<String, BlockUpdate> blockUpdates;

    @Test
    void check() {
        ReflectionTestUtils.setField(chaosService, "chaosEnabled", true);
        var result = chaosService.getLatestHit();
        assertThat(result).isNotNull();
    }

    @Test
    void generateSpiral() {
        // execute
        for (int i = 1; i < 100; i++) {
            Pair<Integer, Integer> target = chaosService.calculateTarget(i);
            log.info("Generation {}: X: {}, Y: {}", i, target.getFirst(), target.getSecond());
        }
    }
}