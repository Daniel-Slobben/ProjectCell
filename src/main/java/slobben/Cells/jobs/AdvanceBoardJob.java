package slobben.Cells.jobs;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import slobben.Cells.service.RunnerService;

@Component
@AllArgsConstructor
public class AdvanceBoardJob {

    private static final Logger log = LoggerFactory.getLogger(AdvanceBoardJob.class);
    private final RunnerService runnerService;

    @Scheduled(fixedRate = 1000)
    public void advance() {
        log.info("scheduled trigger");
        runnerService.run();
    }
}
