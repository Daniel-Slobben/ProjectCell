package slobben.Cells.entities.model;

import lombok.Getter;
import lombok.Setter;

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
