package slobben.cells.service.worker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.test.util.ReflectionTestUtils;
import slobben.cells.dto.internal.Coordinates;
import slobben.cells.entities.Block;
import slobben.cells.entities.ChaosHit;
import slobben.cells.service.workers.ChaosService;
import slobben.cells.service.workers.chaosmakers.Maker;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// every maker is replaced by one that writes no cells, so the tests fill the world with blocks by hand
class ChaosServiceTest {

    private final Map<String, Block> blocks = new HashMap<>();
    private final List<ChaosHit> chaosHits = new ArrayList<>();
    private final BeanFactory beanFactory = mock(BeanFactory.class);
    private final ChaosService chaosService = new ChaosService(blocks, beanFactory, chaosHits);
    private int hitAge = 10_000;

    @BeforeEach
    void setUp() {
        Maker maker = (x, y, size) -> new ChaosHit("test", UUID.randomUUID(), new Coordinates(x, y), hitAge, size);
        when(beanFactory.getBean(any(Class.class))).thenReturn(maker);
        ReflectionTestUtils.setField(chaosService, "preferredType", "ANY");
        ReflectionTestUtils.setField(chaosService, "blockSize", 20);
        ReflectionTestUtils.setField(chaosService, "maxBlocks", 3);
        ReflectionTestUtils.setField(chaosService, "targetSpeed", 100);
    }

    @Test
    void spawnsAHitEveryHundredTicks() {
        // execute
        tick(100);
        int hitsAfterHundredTicks = chaosHits.size();
        tick(1);

        // verify
        assertThat(hitsAfterHundredTicks).isEqualTo(1);
        assertThat(chaosHits).hasSize(2);
    }

    @Test
    void stopsSpawningAtTheBlockLimit() {
        // prepare
        tick(1);
        addBlocks(3);

        // execute
        tick(1000);

        // verify
        assertThat(chaosHits).hasSize(1);
        assertThat(blocks).hasSize(3);
    }

    @Test
    void resetsTheWorldWhenAllHitsAgedOut() {
        // prepare
        hitAge = 50;
        tick(1);
        addBlocks(2);
        tick(50);
        int blocksAtMaxAge = blocks.size();

        // execute
        tick(1);

        // verify
        assertThat(blocksAtMaxAge).isEqualTo(2);
        assertThat(blocks).isEmpty();
        assertThat(chaosHits).singleElement().extracting(ChaosHit::getAge).isEqualTo(0);
    }

    @Test
    void resetsTheWorldAfterTwentySlowCyclesInARow() {
        // prepare
        ReflectionTestUtils.setField(chaosService, "maxCycleShare", 0.5);
        tick(1);
        addBlocks(2);
        recordCycles(20, 51);

        // execute
        tick(1);

        // verify
        assertThat(blocks).isEmpty();
    }

    @Test
    void aFastCycleStartsTheSlowCountAgain() {
        // prepare
        ReflectionTestUtils.setField(chaosService, "maxCycleShare", 0.5);
        tick(1);
        addBlocks(2);
        recordCycles(19, 51);
        recordCycles(1, 50);
        recordCycles(19, 51);

        // execute
        tick(1);

        // verify
        assertThat(blocks).hasSize(2);
    }

    @Test
    void spawnsNothingWhileTheLastCycleWasSlow() {
        // prepare
        ReflectionTestUtils.setField(chaosService, "maxCycleShare", 0.5);
        tick(1);
        recordCycles(1, 51);

        // execute
        tick(200);
        int hitsWhileSlow = chaosHits.size();
        recordCycles(1, 50);
        tick(1);

        // verify
        assertThat(hitsWhileSlow).isEqualTo(1);
        assertThat(chaosHits).hasSize(2);
    }

    @Test
    void theCycleGuardIsOffAtAShareOfZero() {
        // prepare
        tick(1);
        addBlocks(2);
        recordCycles(100, 10_000);

        // execute
        tick(1);

        // verify
        assertThat(blocks).hasSize(2);
    }

    private void tick(int ticks) {
        for (int i = 0; i < ticks; i++) {
            chaosService.execute();
        }
    }

    private void addBlocks(int amount) {
        for (int i = 0; i < amount; i++) {
            Block block = new Block(i, 0, UUID.randomUUID(), 20);
            blocks.put(block.getKey(), block);
        }
    }

    private void recordCycles(int amount, long millis) {
        for (int i = 0; i < amount; i++) {
            chaosService.recordCycleTime(millis);
        }
    }
}
