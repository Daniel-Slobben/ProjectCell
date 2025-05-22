package slobben.Cells.service;

import lombok.AllArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class GenerationService {

    @Setter
    private boolean running = true;
    private final StateService stateService;
    private final GameService gameService;

    public GenerationService(StateService stateService, GameService gameService) {
        this.stateService = stateService;
        this.gameService = gameService;
    }

    @SneakyThrows
    public void run() {
        while(running) {
            long timer = System.currentTimeMillis();
            Thread.sleep(250);
            log.info("Starting run. Generation: {}", stateService.getCurrentGeneration());
            gameService.setNextState();
            log.info("Ending run. Time Taken: {}ms", System.currentTimeMillis() - timer);
        }
    }

}
