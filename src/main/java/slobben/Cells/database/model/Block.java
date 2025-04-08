package slobben.Cells.database.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@CompoundIndex(name = "x_y", def = "{'x': 1, 'y': 1}")
@NoArgsConstructor
@Document("blocks")
@Getter
@Setter
public class Block {

    @Id
    private String id;
    private int x;
    private int y;
    private int generation;

    public Block(int x, int y, int generation) {
        this.x = x;
        this.y = y;
        this.generation = generation;
    }
}
