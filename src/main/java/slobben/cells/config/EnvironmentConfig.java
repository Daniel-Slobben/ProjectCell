package slobben.cells.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Getter
public class EnvironmentConfig {

    @Value("${cells.size.blockSize}")
    private int blockSize;

    @Value("${cells.size.y}")
    private int sizeY;


    public int getBlockAmount() {
        return sizeY / blockSize;
    }

    public int getBlockSizeWithBorder() {
        return blockSize + 2;
    }
}
