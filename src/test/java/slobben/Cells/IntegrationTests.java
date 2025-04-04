package slobben.Cells;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import slobben.Cells.service.GameService;
import slobben.Cells.service.StateService;

import static org.hamcrest.MatcherAssert.assertThat;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@ComponentScan("slobben.Cells.service")
@TestPropertySource(properties = {
		"properties.size.x = 5",
		"properties.size.y = 5"
})
class IntegrationTests {

	private final StateService stateService;
	private final GameService gameService;
	private final ViewService viewService;

	@Autowired
	public IntegrationTests(GameService gameService, StateService stateService, ViewService viewService) {
		this.gameService = gameService;
		this.stateService = stateService;
		this.viewService = viewService;
	}

	@Test
	public void visualize() {
		viewService.logToConsole();
		gameService.setNextState();
		viewService.logToConsole();
		gameService.setNextState();
		viewService.logToConsole();
		gameService.setNextState();
		viewService.logToConsole();
		gameService.setNextState();
		viewService.logToConsole();
	}
}
