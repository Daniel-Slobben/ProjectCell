package slobben.cells.service.worker;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import slobben.cells.dto.internal.Coordinates;
import slobben.cells.entities.Block;
import slobben.cells.entities.ChaosHit;
import slobben.cells.service.ExecutorService;
import slobben.cells.service.RleReader;
import slobben.cells.service.WorldEditor;
import slobben.cells.service.workers.*;
import slobben.cells.service.workers.chaosmakers.SquareMaker;
import slobben.cells.util.BlockUtils;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

// a spacefiller fills its blocks with zebra stripes: the densest sectors in the world, with nothing happening in them
class ViewportSpacefillerTest {
    private static final int SIZE = 500;
    private static final int SECTOR_SIZE = 50;

    private final Map<String, Block> blocks = new HashMap<>();
    private final Map<String, Block> newBlocks = new ConcurrentHashMap<>();
    private final ExecutorService executorService = new ExecutorService();
    private final AlgorithmGol algorithmGol = new AlgorithmGol(executorService, blocks);
    private final Stitching stitching = new Stitching(executorService, blocks, newBlocks);
    private final AddNewBlocks addNewBlocks = new AddNewBlocks(newBlocks, blocks);
    private final WorldEditor worldEditor = new WorldEditor(newBlocks);
    private final RleReader rleReader = new RleReader(worldEditor);
    private final ViewportService viewportService = new ViewportService(blocks, new ArrayList<ChaosHit>());

    @Test
    void picksChaosOverIntactSpacefillers() throws IOException {
        // prepare
        setSizes();
        UUID square = new SquareMaker(worldEditor).getChaosHit(0, 4000, 1500).getId();
        // both directions of stripes
        rleReader.writePatternToWorld("spacefiller1", new Coordinates(0, 0), UUID.randomUUID(), (byte) 0);
        rleReader.writePatternToWorld("spacefiller2", new Coordinates(4000, 0), UUID.randomUUID(), (byte) 1);
        rleReader.writePatternToWorld("spacefiller2", new Coordinates(-4000, 0), UUID.randomUUID(), (byte) 2);
        for (int generation = 0; generation < 900; generation++) {
            algorithmGol.execute();
            addNewBlocks.execute();
            stitching.execute();
            addNewBlocks.execute();
        }

        // execute
        viewportService.execute();

        // verify
        assertThat(collectPickedChaosHits()).containsOnly(square);
    }

    private void setSizes() {
        ReflectionTestUtils.setField(algorithmGol, "blockSize", SIZE);
        ReflectionTestUtils.setField(algorithmGol, "sectorSize", SECTOR_SIZE);
        ReflectionTestUtils.invokeMethod(algorithmGol, "init");
        ReflectionTestUtils.setField(stitching, "blockSize", SIZE);
        ReflectionTestUtils.invokeMethod(stitching, "init");
        ReflectionTestUtils.setField(worldEditor, "blockSize", SIZE);
        ReflectionTestUtils.setField(viewportService, "blockSize", SIZE);
        ReflectionTestUtils.setField(viewportService, "sectorSize", SECTOR_SIZE);
    }

    // the pick is random, so ask often enough to see every candidate
    private Set<UUID> collectPickedChaosHits() {
        return IntStream.range(0, 1000)
                .mapToObj(i -> viewportService.getInterestingViewport(List.of()))
                .map(picked -> blocks.get(BlockUtils.getKey(Math.floorDiv(picked.x(), SIZE), Math.floorDiv(picked.y(), SIZE))))
                .map(Block::getResponsibleChaosHit)
                .collect(Collectors.toSet());
    }
}
