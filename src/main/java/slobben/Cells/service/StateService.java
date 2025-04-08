package slobben.Cells.service;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import slobben.Cells.database.model.Block;
import slobben.Cells.database.model.Cell;
import slobben.Cells.database.repository.BlockRepository;
import slobben.Cells.database.repository.CellRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Getter
public class StateService {

    private final CellRepository cellRepository;
    private final BlockRepository blockRepository;
    private final MongoTemplate mongoTemplate;
    private final int sizeX;
    private final int sizeY;
    private final int blockSize;
    private int currentGeneration = 0;

    public StateService(CellRepository cellRepository, BlockRepository blockRepository, MongoTemplate mongoTemplate, @Value("${properties.size.blockSize}") int blockSize, @Value("${properties.size.x}") int sizeX, @Value("${properties.size.y}") int sizeY) {
        this.mongoTemplate = mongoTemplate;

        this.cellRepository = cellRepository;
        this.blockRepository = blockRepository;
        this.sizeX = sizeX;
        this.blockSize = blockSize;
        this.sizeY = sizeY;

        initializeMap(cellRepository, blockRepository, blockSize, sizeX, sizeY);
    }

    private void initializeMap(CellRepository cellRepository, BlockRepository blockRepository, int blockSize, int sizeX, int sizeY) {
        mongoTemplate.dropCollection(Cell.class);
        mongoTemplate.dropCollection(Block.class);
        int blockAmountX = sizeX / blockSize;
        int blockAmountY = sizeY / blockSize;
        ExecutorService executor = Executors.newFixedThreadPool(24);

        for (int blockX = 0; blockX < blockAmountX; blockX++) {
            for (int blockY = 0; blockY < blockAmountY; blockY++) {
                int finalBlockX = blockX;
                int finalBlockY = blockY;
                blockRepository.save(new Block(finalBlockX, finalBlockY, 0));
                executor.execute(() -> {
                    Random random = new Random();
                    List<Cell> newCells = new ArrayList<>();

                    cellRepository.saveAll(newCells);
                });
            }
        }
        executor.shutdown();
    }

    public List<Cell> getLatestState(int x, int y, int size) {
        int xMin = x - (size / 2);
        int xMax = x + (size / 2);
        int yMin = y - (size / 2);
        int yMax = y + (size / 2);
        return cellRepository.getMatrix(xMin, xMax, yMin, yMax);
    }

    public Cell[][] getMatrixState(int xToFind, int yToFind, int size) {
        List<Cell> cellList = this.getLatestState(xToFind, yToFind, size);
        Cell[][] cells = new Cell[size][size];
        cellList.forEach(cell -> cells[cell.getX()][cell.getY()] = cell);
        return cells;
    }

    public void incrementGeneration() {
        this.currentGeneration++;
    }
}
