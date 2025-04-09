package slobben.Cells.database.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import slobben.Cells.database.model.Block;

import java.util.Optional;

@Repository
public interface BlockRepository extends MongoRepository<Block, String> {

    Optional<Block> findFirstByXAndYOrderByGenerationDesc(int x, int y);
}