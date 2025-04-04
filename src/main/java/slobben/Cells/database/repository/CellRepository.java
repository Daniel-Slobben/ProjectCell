package slobben.Cells.database.repository;

import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import slobben.Cells.database.model.Cell;

import java.util.List;

@Repository
public interface CellRepository extends CrudRepository<Cell, String> {

    @Query("{'generation': ?0, 'x': {$gte: ?1, $lte: ?2}, 'y': {$gte: ?3, $lte: ?4}")
    List<Cell> findSubsetMatrix(int generation, int xMin, int xMax, int yMin, int yMax);
}