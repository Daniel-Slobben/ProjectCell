package slobben.Cells;

import slobben.Cells.enums.CellState;

public class CellUtils {

    public static CellState toggleState(CellState cellState) {
        if (cellState == CellState.ALIVE) {
            return CellState.ALIVE;
        } else {
            return CellState.DEAD;
        }
    }
}
