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

    // above the minimum of two sector sizes, and not a multiple of 40, which counts as pure lines
    private static final int BUSY = 101;

    private final Map<String, Block> blocks = new HashMap<>();
    private final List<ChaosHit> chaosHits = new ArrayList<>();
    private final ViewportService viewportService = new ViewportService(blocks, chaosHits);

    @Test
    void aBlockOffersOnlyItsBestSector() {
        // prepare
        Block block = addBlock(0, 0, UUID.randomUUID());
        block.getSectorCounts()[1][2] = BUSY;
        block.getSectorCounts()[3][0] = BUSY + 100;
        Coordinates bestSector = new Coordinates(3 * 40, 0);
        Coordinates otherBlock = addBusyBlock(1, 0);

        // execute
        viewportService.execute();

        // verify
        assertThat(pickedViewports(List.of())).containsExactlyInAnyOrder(bestSector, otherBlock);
    }

    @Test
    void keepsTheFiftyBusiestBlocks() {
        // prepare
        List<Coordinates> quietestFirst = IntStream.range(0, 55)
                .mapToObj(i -> addBusyBlock(i, 0, BUSY + 2 * i, UUID.randomUUID()))
                .toList();
        List<Coordinates> fiftyBusiest = quietestFirst.subList(5, 55);

        // execute
        viewportService.execute();

        // verify
        assertThat(pickedViewports(List.of())).containsExactlyInAnyOrderElementsOf(fiftyBusiest);
    }

    @Test
    void keepsOnlyThreeGrowthBlocks() {
        // prepare
        UUID growth = addChaosHit(ChaosType.GROWTH_PATTERN);
        List<Coordinates> growthBlocks = IntStream.range(0, 5)
                .mapToObj(i -> addBusyBlock(i * 10, 0, BUSY, growth))
                .toList();
        Coordinates square = addBusyBlock(100, 0, BUSY, addChaosHit(ChaosType.SQUARE));

        // execute
        viewportService.execute();

        // verify
        Set<Coordinates> picked = pickedViewports(List.of());
        assertThat(picked).contains(square);
        assertThat(picked).filteredOn(growthBlocks::contains).hasSize(3);
    }

    @Test
    void skipsViewportsWithinThreeBlocksOfRecentOnes() {
        // prepare
        Coordinates recent = new Coordinates(0, 0);
        addBusyBlock(3, -3);
        Coordinates fourBlocksAway = addBusyBlock(4, 0);

        // execute
        viewportService.execute();

        // verify
        assertThat(pickedViewports(List.of(recent))).containsExactly(fourBlocksAway);
    }

    @Test
    void picksAnyViewportWhenAllAreNearRecentOnes() {
        // prepare
        Coordinates first = addBusyBlock(0, 0);
        Coordinates second = addBusyBlock(10, 0);

        // execute
        viewportService.execute();

        // verify
        assertThat(pickedViewports(List.of(first, second))).containsExactlyInAnyOrder(first, second);
    }

    // the pick is random, so ask often enough to see every candidate
    private Set<Coordinates> pickedViewports(List<Coordinates> recent) {
        return IntStream.range(0, 1000)
                .mapToObj(i -> viewportService.getInterestingViewport(recent))
                .collect(Collectors.toSet());
    }

    private UUID addChaosHit(ChaosType type) {
        ChaosHit hit = new ChaosHit(type.name(), UUID.randomUUID(), new Coordinates(0, 0), 100);
        hit.setType(type);
        chaosHits.add(hit);
        return hit.getId();
    }

    private Coordinates addBusyBlock(int x, int y) {
        return addBusyBlock(x, y, BUSY, UUID.randomUUID());
    }

    // returns the viewport the block offers: its top left sector
    private Coordinates addBusyBlock(int x, int y, int count, UUID chaosHit) {
        addBlock(x, y, chaosHit).getSectorCounts()[0][0] = (short) count;
        return new Coordinates(x * 500, y * 500);
    }

    // the service only reads the sector counts, so the block itself can be small
    private Block addBlock(int x, int y, UUID chaosHit) {
        Block block = new Block(x, y, chaosHit, 20);
        block.setSectorCounts(new short[4][4]);
        blocks.put(block.getKey(), block);
        return block;
    }
}
