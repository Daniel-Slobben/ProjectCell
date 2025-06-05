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

    public EnvironmentService(@Value("${properties.size.blockSize}") int blockSize, @Value("${properties.size.x}") int sizeX, @Value("${properties.size.y}") int sizeY) {
        this.sizeX = sizeX;
        this.blockSize = blockSize;
        this.sizeY = sizeY;
        this.blockAmount = sizeY / blockSize;
        this.blockSizeWithBorder = blockSize + 2;
    }
}
