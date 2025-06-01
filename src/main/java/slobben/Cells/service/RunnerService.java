package slobben.Cells.service;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RunnerService {

    @Setter
    private boolean running = true;
    private final BoardManagingService boardManagingService;
    private final GenerationService generationService;

    @SneakyThrows
    public void run() {
        while(running) {
            long timer = System.currentTimeMillis();
            log.info("Starting run. Generation: {}", boardManagingService.getCurrentGeneration());
            generationService.setNextState();
            log.info("Ending run. Time Taken: {}ms", System.currentTimeMillis() - timer);
        }
    }

}
