package slobben.Cells.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import slobben.Cells.database.model.Block;
import slobben.Cells.database.model.Cell;
import slobben.Cells.database.repository.BlockRepository;

@Service
@RequiredArgsConstructor
public class BoardInfoService {

    private final BlockRepository blockRepository;
    private final EnvironmentService environmentService;

    public Cell[][] getBlock(int x, int y) {
        return getBlock(blockRepository.findByXAndY(x, y));
    }

    public Cell[][] getBlockWithoutBorder(int x, int y) {
        return getBlockWithoutBorder(blockRepository.findByXAndY(x, y));
    }

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
            for (int j = 1; j < environmentService.getBlockSizeWithBorder() - 1; j++) {
                map[i-1][j-1] = mapWithBorder[i][j];
            }
        }
        return map;
    }
}