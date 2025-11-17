package slobben.Cells.entities.model;

import lombok.*;

import java.util.Map;

@Getter
@Setter
@RequiredArgsConstructor
@AllArgsConstructor
@Builder
public class Block {

    private final int x;
    private final int y;
    private int generation = 0;
    private boolean isUpdatingWeb = false;
    private boolean ghostBlock = false;
    private final boolean[][] cells;
}
