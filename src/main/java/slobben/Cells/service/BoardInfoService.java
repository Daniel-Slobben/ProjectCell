package slobben.Cells.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import slobben.Cells.entities.model.Block;

@Service
@RequiredArgsConstructor
public class BoardInfoService {
    private final EnvironmentService environmentService;

    public boolean[][] getBlockWithoutBorder(Block block) {
        var mapWithBorder = block.getCells();
        boolean[][] map = new boolean[environmentService.getBlockSize()][environmentService.getBlockSize()];
        for (int i = 1; i < environmentService.getBlockSizeWithBorder() - 1; i++) {
            System.arraycopy(mapWithBorder[i], 1, map[i - 1], 0, environmentService.getBlockSizeWithBorder() - 1 - 1);
        }
        return map;
    }
}