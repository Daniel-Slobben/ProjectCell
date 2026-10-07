package slobben.cells.service.workers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import org.springframework.stereotype.Service;
import slobben.cells.dto.outgoing.EncodedBlock;
import slobben.cells.dto.outgoing.EncodedBlockType;
import slobben.cells.entities.Block;
import slobben.cells.enums.BlockState;
import slobben.cells.service.ExecutorService;

import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Slf4j
public class PackingService implements Worker {
    private static final LZ4Compressor compressor = LZ4Factory.fastestInstance().fastCompressor();
    private final Map<String, Block> blocks;
    private final ExecutorService executorService;

    private static EncodedBlock packBlock(Block block) {
        byte[] packed = getPacked(block.getCells());
        byte[] compressed = compressor.compress(packed);
        return new EncodedBlock(block.getX(), block.getY(), block.getGeneration(), Base64.getEncoder().encodeToString(compressed), EncodedBlockType.FULL.name());
    }

    @Override
    public String getName() {
        return "Caching Encoded Blocks";
    }

    private static EncodedBlock packBorders(Block block) {
        if (BlockState.NEW.equals(block.getBlockState())) {
            return packBlock(block);
        }
        byte[] packed = getPackedBorders(block.getCells());
        byte[] compressed = compressor.compress(packed);
        return new EncodedBlock(block.getX(), block.getY(), block.getGeneration(), Base64.getEncoder().encodeToString(compressed), EncodedBlockType.BORDER.name());
    }

    private static void setBit(byte[] packed, int i) {
        packed[i / 8] |= (byte) (1 << (i % 8));
    }

    private static byte[] getPackedBorders(boolean[][] cells) {
        final int min = 1;
        final int max = cells.length - 2;
        final int size = max - min + 1;
        final int totalBits = size * 4 - 4;

        int index = 0;

        byte[] packed = new byte[(totalBits + 7) / 8];

        for (int i = min; i <= max; i++) {
            if (cells[i][max]) setBit(packed, index);
            index++;
        }
        for (int i = min; i <= max; i++) {
            if (cells[i][min]) setBit(packed, index);
            index++;
        }
        for (int i = min + 1; i < max; i++) {
            if (cells[min][i]) setBit(packed, index);
            index++;
        }
        for (int i = min + 1; i < max; i++) {
            if (cells[max][i]) setBit(packed, index);
            index++;
        }
        if (index != totalBits) {
            throw new IllegalStateException("index=" + index + " totalBits=" + totalBits + " size=" + size + " cells=" + cells.length);
        }
        return packed;
    }

    private static byte[] getPacked(boolean[][] cells) {
        final int size = cells.length - 2;
        final int totalBits = size * size;
        final int startingIndex = 1;
        byte[] packed = new byte[(totalBits + 7) / 8];

        for (int xrow = startingIndex; xrow < cells.length - startingIndex; xrow++) {
            for (int ycol = startingIndex; ycol < cells.length - startingIndex; ycol++) {
                int i = (xrow - startingIndex) * size + (ycol - startingIndex);
                if (cells[xrow][ycol]) {
                    setBit(packed, i);
                }
            }
        }
        return packed;
    }

    @Override
    public void execute() {
        Set<Runnable> tasks = blocks.values().stream().map(block -> (Runnable) () -> {
            block.setEncodedBlock(packBlock(block));
            block.setEncodedBlockBorders(packBorders(block));
        }).collect(Collectors.toSet());

        executorService.executeTasksParallel(tasks, getName());
    }
}



