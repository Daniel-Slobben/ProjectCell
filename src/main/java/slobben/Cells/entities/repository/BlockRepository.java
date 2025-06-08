package slobben.Cells.entities.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import slobben.Cells.entities.model.Block;

import java.util.List;

@Repository
public interface BlockRepository extends MongoRepository<Block, String> {

    Block findByXAndY(int x, int y);
    List<Block> findByX(int x);
}