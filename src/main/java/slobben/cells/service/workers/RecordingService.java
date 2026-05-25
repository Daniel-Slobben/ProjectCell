package slobben.cells.service.workers;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import slobben.cells.entities.model.Block;
import slobben.cells.enums.BlockState;

import java.util.*;

import static slobben.cells.enums.BlockState.ACTIVE;

@Service
@RequiredArgsConstructor
public class RecordingService implements Worker {
    private final Map<String, Block> blocks;
    private final Set<Block> checkedBlock = new HashSet<>();
    @Value("${cells.recording.max-blocks:10}")
    private int maxBlocks;
    private final Set<Block> currentlyRecording = HashSet.newHashSet(maxBlocks);
    @Value("${cells.recording.max-recording-depth:10}")
    private int maxDepth;

    @Override
    public void execute() {
        // add new blocks to check if possible
        blocks.values().stream()
                .filter(block -> !currentlyRecording.contains(block))
                .filter(block -> !checkedBlock.contains(block))
                .filter(block -> ACTIVE.equals(block.getBlockState()))
                .takeWhile(_ -> currentlyRecording.size() < maxBlocks)
                .forEach(currentlyRecording::add);

        // reset checkBlock when currentlyRecording is not full
        if (currentlyRecording.size() != maxBlocks) {
            checkedBlock.clear();
        }

        // add recording to block
        currentlyRecording.forEach(block -> block.getRecordings().add(block.getCells()));

        // check for duplicate states
        Iterator<Block> currentlyRecordingIterator = currentlyRecording.iterator();
        while (currentlyRecordingIterator.hasNext()) {
            Block block = currentlyRecordingIterator.next();

            if (block.getRecordings().size() == 1) continue;

            boolean[][] firstState = block.getRecordings().getFirst();
            boolean[][] lastState = block.getRecordings().getLast();

            if (Arrays.deepEquals(firstState, lastState)) {
                block.setBlockState(BlockState.HIBERNATION);
                currentlyRecordingIterator.remove();
                block.getRecordings().removeLast();
            } else if (block.getRecordings().size() == maxDepth) {
                currentlyRecordingIterator.remove();
                checkedBlock.add(block);
                block.getRecordings().clear();
            }
        }
    }

    @Override
    public String getName() {
        return "Recording";
    }
}
