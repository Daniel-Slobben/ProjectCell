package slobben.Cells.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import slobben.Cells.entities.model.Block;

@Service
@RequiredArgsConstructor
public class BoardInfoService {
    private final EnvironmentService environmentService;

    public byte[][] getBlockWithoutBorder(Block block) {
        var mapWithBorder = block.getCells();
        byte[][] map = new byte[environmentService.getBlockSize()][environmentService.getBlockSize()];
        for (int i = 1; i < environmentService.getBlockSizeWithBorder() - 1; i++) {
            System.arraycopy(mapWithBorder[i], 1, map[i - 1], 0, environmentService.getBlockSize());
        }
        return map;
    }
}