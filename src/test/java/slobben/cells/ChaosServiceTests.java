package slobben.cells;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;
import slobben.cells.dto.BlockUpdate;
import slobben.cells.service.workers.chaos.ChaosService;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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
        chaosService.getLatestHit();
        assertThat(blockUpdates).hasSizeGreaterThan(4);
    }
}