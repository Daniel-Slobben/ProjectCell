package slobben.Cells.jobs;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import slobben.Cells.service.GameService;

@Component
@AllArgsConstructor
public class AdvanceBoardJob {

    private static final Logger log = LoggerFactory.getLogger(AdvanceBoardJob.class);
    private final GameService gameService;

    @Scheduled(fixedRate = 2000)
    public void advance() {
       gameService.setNextState();
       log.info("Advancing Board");
    }
}
