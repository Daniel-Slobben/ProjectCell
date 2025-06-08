package slobben.Cells;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import slobben.Cells.entities.model.Block;
import slobben.Cells.entities.model.Cell;
import slobben.Cells.service.GenerationService;
import slobben.Cells.service.InitializerService;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@ComponentScan("slobben.Cells.service")
@ActiveProfiles(profiles = "unit")
class GenerationServiceTests {

	private final InitializerService initializerService;
	private final GenerationService generationService;

	@Autowired
	public GenerationServiceTests(GenerationService generationService, InitializerService initializerService) {
		this.generationService = generationService;
		this.initializerService = initializerService;
	}

	@Test
	public void checkTick() {
		var blocks = initializerService.initializeMap();
		Block block = blocks.get(0).get(0);
		HashMap<Integer, HashMap<Integer, Cell>> cellsToAdd = new HashMap<>();
		cellsToAdd.computeIfAbsent(0, row -> new HashMap<>()).put(0, new Cell(0, 0));
		cellsToAdd.computeIfAbsent(0, row -> new HashMap<>()).put(1, new Cell(0, 1));
		cellsToAdd.computeIfAbsent(1, row -> new HashMap<>()).put(0, new Cell(1, 0));
		cellsToAdd.computeIfAbsent(1, row -> new HashMap<>()).put(1, new Cell(1, 1));
		block.getCells().putAll(cellsToAdd);
		generationService.setNextState(block);

		assertThat(block.getCells().get(0).get(0)).isNotNull();
		assertThat(block.getCells().get(0).get(1)).isNotNull();
		assertThat(block.getCells().get(1).get(0)).isNotNull();
		assertThat(block.getCells().get(1).get(1)).isNotNull();
	}
}