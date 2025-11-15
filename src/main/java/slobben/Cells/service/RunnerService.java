package slobben.Cells.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import slobben.Cells.config.BlockUpdate;
import slobben.Cells.config.StateInfo;
import slobben.Cells.controller.ClientUpdateRequest;
import slobben.Cells.entities.model.Block;
import slobben.Cells.util.BlockUtils;

import java.sql.Array;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static java.lang.Thread.sleep;

@Service
@RequiredArgsConstructor
@Slf4j
public class RunnerService {

    private final StitchingService stitchingService;
    private final BoardInfoService boardInfoService;
    private final InitializerService initializerService;
    private final GenerationService generationService;
    private final UpdateWebService updateWebService;
    private final EnvironmentService environmentService;
    ArrayList<Block> blocks;
    @Setter
    private boolean running = true;
    @Getter
    List<BlockUpdate> blockUpdates = new ArrayList<>();
    private final Map<UUID, Set<Block>> activeClients = new HashMap<>();

    @SneakyThrows
    public void run() {
        this.blocks = initializerService.initializeMap();
        do {
            long timer = System.currentTimeMillis();
            log.info("");
            log.info("Starting run!");

            // TODO: No stitch happens in first run after initializing
            stitchingService.resetStitch();
            List<Block> newBlocks = new ArrayList<>();

            forEachBlockParallel("Initialize", stitchingService::initializeStitch);
            forEachBlockParallel("Generate", (block) -> {
                // Check for block update request by user
                if (!blockUpdates.isEmpty()) {
                    var optionalBlockUpdate = blockUpdates.stream().filter(update -> update.x() == block.getX() && update.y() == block.getY()).findFirst();
                    if (optionalBlockUpdate.isPresent()) {
                        blockUpdates.remove(optionalBlockUpdate.get());
                        updateBlock(block, optionalBlockUpdate.get());

                        return;
                    }
                }
                if (!block.isGhostBlock()) {
                    generationService.setNextState(block);
                }
            });
            // create a new block for every update request that hasnt happened yet
            if (!blockUpdates.isEmpty()) {
                blockUpdates.forEach(updateBlock -> {
                    log.info("Generating new block for: x{}, y{}", updateBlock.x(), updateBlock.y());
                    boolean[][] matrix = new boolean[environmentService.getBlockSizeWithBorder()][environmentService.getBlockSizeWithBorder()];
                    Block newBlock = new Block(updateBlock.x(), updateBlock.y(), matrix);
                    stitchingService.initializeStitch(newBlock);
                    updateBlock(newBlock, updateBlock);
                    blocks.add(newBlock);
                });
                blockUpdates.clear();
            }

            forEachBlockParallel("AddBorderCells", block -> newBlocks.addAll(stitchingService.addBorderCells(block)));
            blocks.addAll(newBlocks);

            activeClients.entrySet().stream().parallel().forEach((entry) -> updateWebService.updateClient(entry.getKey(), entry.getValue()));
            forEachBlockParallel("Stitch", stitchingService::stitchBlock);

            long timeTaken = System.currentTimeMillis() - timer;
            long timeDelta = timeTaken - environmentService.getTargetspeed();
            if (timeDelta < 0) {
                sleep(Math.abs(timeDelta));
                log.info("Ending run. Time Taken: {}ms, Waited for {}ms", timeTaken, Math.abs(timeDelta));
            } else {
                log.info("Ending run. Time Taken: {}ms, No waiting!", timeTaken);
            }
        } while (running);
    }

    private void updateBlock(Block block, BlockUpdate update) {
        for (int x = 1; x < update.state().length + 1; x++) {
            System.arraycopy(update.state()[x - 1], 0, block.getCells()[x], 1, update.state().length);
        }
    }

    private Block getBlock(int x, int y) throws IllegalStateException {
        var optionalBlock = blocks.stream().filter(block -> block.getX() == x && block.getY() == y).findFirst();
        if (optionalBlock.isEmpty()) {
            throw new IllegalStateException("No block found for x: " + x + ", y: " + y);
        }
        return optionalBlock.get();
    }

    private void forEachBlockParallel(String taskName, Consumer<Block> task) throws InterruptedException {
        forEachParallel(taskName, blocks, task);
    }

    private void forEachParallel(String taskName, List<Block> blocks, Consumer<Block> task) throws InterruptedException {
        long timer = System.currentTimeMillis();
        ExecutorService executor = Executors.newFixedThreadPool(16);

        blocks.forEach(block -> executor.execute(() -> task.accept(block)));

        executor.shutdown();
        if (!executor.awaitTermination(120, TimeUnit.SECONDS)) {
            log.warn("Executor did not shut down cleanly within timeout.");
        }
        log.info("Task {} finished in: {}ms", taskName, System.currentTimeMillis() - timer);
    }

    public boolean[][] setBlockUpdate(int x, int y, boolean update) {
        try {
            var block = getBlock(x, y);
            block.setUpdatingWeb(update);

            if (!update && block.isGhostBlock()) {
                blocks.remove(block);
            }
            return boardInfoService.getBlockWithoutBorder(block);
        } catch (IllegalStateException e) {
            if (update) {
                getNewGhostBlock(Pair.of(x, y));
            }
            return new boolean[environmentService.getBlockSizeWithBorder()][environmentService.getBlockSizeWithBorder()];
        }
    }

    public StateInfo getStateInfo() {
        return StateInfo.builder()
                .blocksInMemory(blocks.size())
                .blocksUpdating((int)(blocks.stream().filter(Block::isUpdatingWeb).count()))
                .build();
    }

    public void setBlock(int x, int y, boolean[][] body) {
        blockUpdates.add(BlockUpdate.builder().x(x).y(y).state(body).build());
    }

    public Set<Block> getBlocksFromKeys(Set<String> keys) {
        Set<Block> blocksToAdd = new HashSet<>();
        for (String key : keys) {
            var coordinates = BlockUtils.resolveKey(key);
            try {
                blocksToAdd.add(getBlock(coordinates.getFirst(), coordinates.getSecond()));
            } catch (IllegalStateException e) {
                Block newBlock = getNewGhostBlock(coordinates);
                blocksToAdd.add(newBlock);
            }
        }
        return blocksToAdd;
    }

    public void updateClient(ClientUpdateRequest clientUpdateRequest) {
        if (activeClients.containsKey(clientUpdateRequest.client())) {
            var clientBlocks = activeClients.get(clientUpdateRequest.client());

            clientBlocks.removeIf(block -> Set.of(clientUpdateRequest.blocksToRemove()).contains(BlockUtils.getKey(block.getX(), block.getY())));
            clientBlocks.addAll(getBlocksFromKeys(Set.of(clientUpdateRequest.blocksToAdd())));
        } else {
           activeClients.put(clientUpdateRequest.client(), getBlocksFromKeys(Set.of(clientUpdateRequest.blocksToAdd())));
        }
    }

    private Block getNewGhostBlock(Pair<Integer, Integer> coordinates) {
        Block newBlock = new Block(coordinates.getFirst(), coordinates.getSecond(), new boolean[environmentService.getBlockSizeWithBorder()][environmentService.getBlockSizeWithBorder()]);
        newBlock.setGhostBlock(true);
        blocks.add(newBlock);
        return newBlock;
    }
}
