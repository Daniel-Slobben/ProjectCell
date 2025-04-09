package slobben.Cells.database.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
public class Cell {

    private int x;
    private int y;

    public Cell(int x, int y) {
        this.x = x;
        this.y = y;
    }
}
