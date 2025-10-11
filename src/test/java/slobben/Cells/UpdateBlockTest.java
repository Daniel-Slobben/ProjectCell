package slobben.Cells;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.env.Environment;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import slobben.Cells.config.BlockUpdate;
import slobben.Cells.service.EnvironmentService;
import slobben.Cells.service.RunnerService;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@ComponentScan("slobben.Cells.service")
@ActiveProfiles(profiles = "unit")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class UpdateBlockTest {
    private final RunnerService runnerService;
    private final EnvironmentService environmentService;

    @Autowired
    public UpdateBlockTest(RunnerService runnerService, EnvironmentService environmentService) {
        this.runnerService = runnerService;
        this.environmentService = environmentService;
    }

    @Test
    public void manualBlockUpdateTest() {
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
        boolean[][] blockAfterGeneration = runnerService.setBlockUpdate(0, 0, true);
        assertThat(blockAfterGeneration).isEqualTo(body);
    }
}
