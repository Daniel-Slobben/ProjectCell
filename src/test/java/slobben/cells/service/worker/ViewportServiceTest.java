package slobben.cells.service.worker;

import org.junit.jupiter.api.Test;
import slobben.cells.dto.internal.Coordinates;
import slobben.cells.entities.Block;
import slobben.cells.entities.ChaosHit;
import slobben.cells.enums.ChaosType;
import slobben.cells.service.workers.ViewportService;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

// without Spring the service uses its defaults: blocks of 500 cells and sectors of 40
class ViewportServiceTest {

    private final Map<String, Block> blocks = new HashMap<>();
    private final List<ChaosHit> chaosHits = new ArrayList<>();
    private final ViewportService viewportService = new ViewportService(blocks, chaosHits);

    @Test
    void aBlockOffersOnlyItsBestSector() {
        // prepare
        Block busy = block(0, 0);
        busy.getSectorCounts()[1][2] = 101;
        busy.getSectorCounts()[3][0] = 201;
        block(1, 0).getSectorCounts()[0][1] = 101;

        // execute
        viewportService.execute();

        // verify
        assertThat(viewports(List.of())).containsExactlyInAnyOrder(new Coordinates(120, 0), new Coordinates(500, 40));
    }

    @Test
    void keepsTheFiftyBusiestBlocks() {
        // prepare
        for (int i = 0; i < 55; i++) {
            // odd counts, so no sector is filtered as a pure line
            block(i, 0).getSectorCounts()[0][0] = (short) (101 + 2 * i);
        }

        // execute
        viewportService.execute();

        // verify
        assertThat(viewports(List.of())).containsExactlyInAnyOrderElementsOf(
                IntStream.range(5, 55).mapToObj(i -> new Coordinates(i * 500, 0)).toList());
    }

    @Test
    void skipsViewportsWithinThreeBlocksOfRecentOnes() {
        // prepare
        block(0, 0).getSectorCounts()[0][0] = 101;
        block(10, 0).getSectorCounts()[0][0] = 101;
        block(20, 0).getSectorCounts()[0][0] = 101;
        // 3 blocks away (1500 cells) is still near, one cell further is not
        List<Coordinates> recent = List.of(new Coordinates(1500, -1500), new Coordinates(5000 + 1501, 0));

        // execute
        viewportService.execute();

        // verify
        assertThat(viewports(recent)).containsExactlyInAnyOrder(new Coordinates(5000, 0), new Coordinates(10000, 0));
    }

    @Test
    void skipsViewportsOfTheSameChaosHitAsRecentOnes() {
        // prepare
        UUID hit = UUID.randomUUID();
        block(0, 0, hit).getSectorCounts()[0][0] = 101;
        block(10, 0, hit).getSectorCounts()[0][0] = 101;
        block(20, 0).getSectorCounts()[0][0] = 101;

        // execute
        viewportService.execute();

        // verify
        assertThat(viewports(List.of(new Coordinates(0, 0)))).containsExactly(new Coordinates(10000, 0));
    }

    @Test
    void skipsChaosTypesOfTheLatestThreeRecentViewports() {
        // prepare
        block(0, 0, chaosHit(ChaosType.SQUARE));
        block(10, 0, chaosHit(ChaosType.LETTUCE));
        block(20, 0, chaosHit(ChaosType.SPACESHIP));
        block(30, 0, chaosHit(ChaosType.GROWTH_PATTERN));
        block(40, 0, chaosHit(ChaosType.SQUARE)).getSectorCounts()[0][0] = 101;
        block(50, 0, chaosHit(ChaosType.LETTUCE)).getSectorCounts()[0][0] = 101;
        block(60, 0, chaosHit(ChaosType.DIAGONAL_LINES)).getSectorCounts()[0][0] = 101;
        // the square is the oldest of four recent viewports, so only lettuce, spaceship and growth are skipped
        List<Coordinates> recent = List.of(new Coordinates(0, 0), new Coordinates(5000, 0),
                new Coordinates(10000, 0), new Coordinates(15000, 0));

        // execute
        viewportService.execute();

        // verify
        assertThat(viewports(recent)).containsExactlyInAnyOrder(new Coordinates(20000, 0), new Coordinates(30000, 0));
    }

    @Test
    void picksTheSameChaosTypeWhenNothingElseIsLeft() {
        // prepare
        block(0, 0, chaosHit(ChaosType.SQUARE));
        block(10, 0, chaosHit(ChaosType.SQUARE)).getSectorCounts()[0][0] = 101;

        // execute
        viewportService.execute();

        // verify
        assertThat(viewports(List.of(new Coordinates(0, 0)))).containsExactly(new Coordinates(5000, 0));
    }

    @Test
    void picksAnyViewportWhenAllAreNearRecentOnes() {
        // prepare
        block(0, 0).getSectorCounts()[0][0] = 101;
        block(10, 0).getSectorCounts()[0][0] = 101;
        List<Coordinates> recent = List.of(new Coordinates(0, 0), new Coordinates(5000, 0));

        // execute
        viewportService.execute();

        // verify
        assertThat(viewports(recent)).containsExactlyInAnyOrder(new Coordinates(0, 0), new Coordinates(5000, 0));
    }

    // the pick is random, so ask often enough to see every candidate
    private Set<Coordinates> viewports(List<Coordinates> recent) {
        return IntStream.range(0, 1000)
                .mapToObj(i -> viewportService.getInterestingViewport(recent))
                .collect(Collectors.toSet());
    }

    private UUID chaosHit(ChaosType type) {
        ChaosHit hit = new ChaosHit(type.name(), UUID.randomUUID(), new Coordinates(0, 0), 100, 100);
        hit.setType(type);
        chaosHits.add(hit);
        return hit.getId();
    }

    // the service only reads the sector counts, so the block itself can be small
    private Block block(int x, int y) {
        return block(x, y, UUID.randomUUID());
    }

    private Block block(int x, int y, UUID chaosHit) {
        Block block = new Block(x, y, chaosHit, 20);
        block.setSectorCounts(new short[4][4]);
        blocks.put(block.getKey(), block);
        return block;
    }
}
