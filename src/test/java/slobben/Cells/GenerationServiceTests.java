package slobben.Cells;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import slobben.Cells.entities.model.Block;
import slobben.Cells.service.GenerationService;
import slobben.Cells.service.InitializerService;

import java.awt.*;
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
		HashMap<Integer, HashMap<Integer, Integer>> cellsToAdd = new HashMap<>();
		cellsToAdd.computeIfAbsent(1, row -> new HashMap<>()).put(1, Color.BLACK.getRGB());
		cellsToAdd.computeIfAbsent(1, row -> new HashMap<>()).put(2, Color.RED.getRGB());
		cellsToAdd.computeIfAbsent(2, row -> new HashMap<>()).put(1, Color.BLACK.getRGB());
		cellsToAdd.computeIfAbsent(2, row -> new HashMap<>()).put(2, Color.BLACK.getRGB());
		block.getCells().putAll(cellsToAdd);
		generationService.setNextStateNew(block);

		assertThat(block.getCells().get(1).get(1)).isEqualTo(Color.BLACK.getRGB());
		assertThat(block.getCells().get(1).get(2)).isEqualTo(Color.BLACK.getRGB());
		assertThat(block.getCells().get(2).get(1)).isEqualTo(Color.BLACK.getRGB());
		assertThat(block.getCells().get(2).get(2)).isEqualTo(Color.BLACK.getRGB());
	}
}