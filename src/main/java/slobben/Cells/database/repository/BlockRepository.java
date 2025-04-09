package slobben.Cells.database.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import slobben.Cells.database.model.Block;

import java.util.List;
import java.util.Optional;

@Repository
public interface BlockRepository extends MongoRepository<Block, String> {

    @Query("{'x': {$gte: ?0, $lte: ?1}, 'y': {$gte: ?2, $lte: ?3}}")
    List<Block> getMatrix(int xMin, int xMax, int yMin, int yMax);

    Block findByXAndY(int x, int y);
    List<Block> findByX(int x);
}