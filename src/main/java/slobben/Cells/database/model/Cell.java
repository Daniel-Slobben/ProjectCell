package slobben.Cells.database.model;

import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import slobben.Cells.enums.CellState;

@Document(collection = "cells")
@CompoundIndex(name = "generation_x_y_idx", def = "{generation: 1, 'x': 1, 'y': 1}")
@Getter
public class Cell {

    @Id
    private String id;
    private final int generation;
    private final int x;
    private final int y;
    private final CellState cellState;

    public Cell(int generation, int x, int y, CellState cellState) {
        this.generation = generation;
        this.x = x;
        this.y = y;
        this.cellState = cellState;
    }

    public Cell(int x, int y, CellState cellState) {
        this.generation = -1;
        this.x = x;
        this.y = y;
        this.cellState = cellState;
    }
}
