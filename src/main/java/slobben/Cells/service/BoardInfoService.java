package slobben.Cells.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import slobben.Cells.entities.model.Block;

@Service
@RequiredArgsConstructor
public class BoardInfoService {
    private final EnvironmentService environmentService;

    public Integer[][] getBlock(Block block) {
        Integer[][] partialMap = new Integer[environmentService.getBlockSizeWithBorder()][environmentService.getBlockSizeWithBorder()];
        var cells = block.getCells();
        for (var xEntry : cells.entrySet()) {
            for (var yEntry : xEntry.getValue().entrySet()) {
                partialMap[xEntry.getKey()][yEntry.getKey()] = yEntry.getValue();
            }
        }
        return partialMap;
    }

    public Integer[][] getBlockWithoutBorder(Block block) {
        var mapWithBorder = getBlock(block);
        Integer[][] map = new Integer[environmentService.getBlockSize()][environmentService.getBlockSize()];
        for (int i = 1; i < environmentService.getBlockSizeWithBorder() - 1; i++) {
            System.arraycopy(mapWithBorder[i], 1, map[i - 1], 0, environmentService.getBlockSizeWithBorder() - 1 - 1);
        }
        return map;
    }
}