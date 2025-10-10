package slobben.Cells.entities.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@RequiredArgsConstructor
public class Block {

    private final int x;
    private final int y;
    private int generation = 0;
    private boolean isUpdatingWeb = false;
    private final byte[][] cells;
}
