package slobben.Cells.database.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import slobben.Cells.database.model.Cell;

import java.util.List;

@Repository
public interface CellRepository extends MongoRepository<Cell, String> {

    @Query("{'x': {$gte: ?0, $lt: ?1}, 'y': {$gte: ?2, $lt: ?3}}")
    List<Cell> getMatrix(int xMin, int xMax, int yMin, int yMax);
}