package slobben.cells;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import slobben.cells.controller.BlockUpdate;
import slobben.cells.service.EnvironmentService;
import slobben.cells.service.RunnerService;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@ComponentScan("slobben.cells.service")
@ActiveProfiles(profiles = "unit")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class UpdateBlockTest {
    private final RunnerService runnerService;
    private final EnvironmentService environmentService;

    @Autowired
    public UpdateBlockTest(RunnerService runnerService, EnvironmentService environmentService) {
        this.runnerService = runnerService;
        this.environmentService = environmentService;
    }

    @Test
    void manualBlockUpdateTest() {
        // Prepare
        int blockSize = environmentService.getBlockSize();
        boolean[][] body = new boolean[blockSize][blockSize];
        body[0][0] = true;
        body[0][blockSize - 1] = true;
        body[blockSize - 1][0] = true;
        body[blockSize - 1][blockSize - 1] = true;
        BlockUpdate blockUpdate = new BlockUpdate(0, 0, body);
        runnerService.setRunning(false);
        runnerService.getBlockUpdates().add(blockUpdate);

        // Execute
        runnerService.run();

        // Verify
        assertThat(runnerService.getBlockUpdates()).isEmpty();
    }
}
