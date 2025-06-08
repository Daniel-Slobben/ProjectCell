package slobben.Cells.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import slobben.Cells.entities.model.Block;
import slobben.Cells.entities.model.Cell;

@Service
@RequiredArgsConstructor
public class BoardInfoService {
    private final EnvironmentService environmentService;

    public Cell[][] getBlock(Block block) {
        Cell[][] partialMap = new Cell[environmentService.getBlockSizeWithBorder()][environmentService.getBlockSizeWithBorder()];
        var cells = block.getCells();
        for (var xEntry : cells.entrySet()) {
            for (var yEntry : xEntry.getValue().entrySet()) {
                partialMap[xEntry.getKey()][yEntry.getKey()] = yEntry.getValue();
            }
        }
        return partialMap;
    }

    public Cell[][] getBlockWithoutBorder(Block block) {
        var mapWithBorder = getBlock(block);
        Cell[][] map = new Cell[environmentService.getBlockSize()][environmentService.getBlockSize()];
        for (int i = 1; i < environmentService.getBlockSizeWithBorder() - 1; i++) {
            System.arraycopy(mapWithBorder[i], 1, map[i - 1], 0, environmentService.getBlockSizeWithBorder() - 1 - 1);
        }
        return map;
    }
}