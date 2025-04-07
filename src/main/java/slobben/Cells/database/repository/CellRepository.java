package slobben.Cells.database.repository;

import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import slobben.Cells.database.model.Cell;

import java.util.List;
import java.util.Optional;

@Repository
public interface CellRepository extends CrudRepository<Cell, String> {

    @Query("{'generation': ?0, 'x': {$gte: ?1, $lt: ?2}, 'y': {$gte: ?3, $lt: ?4}}")
    List<Optional<Cell>> getMatrix(int generation, int xMin, int xMax, int yMin, int yMax);
}