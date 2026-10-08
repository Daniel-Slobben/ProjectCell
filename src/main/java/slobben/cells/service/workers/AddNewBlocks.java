package slobben.cells.service.workers;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import slobben.cells.entities.Block;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AddNewBlocks implements Worker {

    private final Map<String, Block> newBlocks;
    private final Map<String, Block> blocks;

    @Override
    public String getName() {
        return "Adding blockupdates to blocks";
    }

    @Override
    public void execute() {
        checkForExternalBlockUpdates();
    }

    private void checkForExternalBlockUpdates() {
        newBlocks.values().forEach(newBlock -> blocks.put(newBlock.getKey(), newBlock));
        newBlocks.clear();
    }

}
