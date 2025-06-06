package slobben.Cells.service;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Getter
public class EnvironmentService {

    private final int sizeX;
    private final int sizeY;
    private final int blockSize;
    private final int blockAmount;
    private final int blockSizeWithBorder;
    private final int targetspeed;
    private final String setupMode;
    private final int sparseAmount;
    private final String runMode;

    public EnvironmentService(@Value("${properties.size.blockSize}") int blockSize, @Value("${properties.size.x}") int sizeX, @Value("${properties.size.y}") int sizeY, @Value("${properties.runmode}") String runMode,
                              @Value("${properties.targetspeed}") int targetspeed, @Value("${properties.setup}") String setupMode, @Value("${properties.sparseAmount}") int sparseAmount) {
        this.sizeX = sizeX;
        this.blockSize = blockSize;
        this.sizeY = sizeY;
        this.blockAmount = sizeY / blockSize;
        this.blockSizeWithBorder = blockSize + 2;
        this.targetspeed = targetspeed;
        this.setupMode = setupMode;
        this.sparseAmount = sparseAmount;
        this.runMode = runMode;
    }
}
