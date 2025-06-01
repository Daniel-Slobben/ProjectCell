package slobben.Cells;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import slobben.Cells.service.GenerationService;
import slobben.Cells.service.BoardManagingService;

import static org.hamcrest.MatcherAssert.assertThat;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@ComponentScan("slobben.Cells.service")
@TestPropertySource(properties = {
		"properties.size.x = 32000",
		"properties.size.y = 32000"
})
class IntegrationTests {

	private final BoardManagingService boardManagingService;
	private final GenerationService generationService;

	@Autowired
	public IntegrationTests(GenerationService generationService, BoardManagingService boardManagingService) {
		this.generationService = generationService;
		this.boardManagingService = boardManagingService;
	}

	@Test
	public void visualize() {

	}
}
