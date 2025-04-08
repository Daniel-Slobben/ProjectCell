package slobben.Cells.service;

import lombok.AllArgsConstructor;
import lombok.Setter;
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

       this.run();
    }

    private void run() {
        while(running) {
            log.info("Starting run: " + stateService.getCurrentGeneration());
            gameService.setNextState();
        }
    }

}
