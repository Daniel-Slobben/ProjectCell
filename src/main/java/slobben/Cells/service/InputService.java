package slobben.Cells.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import slobben.Cells.database.model.Cell;
import slobben.Cells.enums.CellState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InputService {

    private ArrayList<Cell> nextStateOverlay = new ArrayList<>();

    public void setCellInOverlay(Cell cell) {
        nextStateOverlay.add(cell);
    }

    private String getGroupingByKey(Cell cell) {
        return cell.getX() + "/" + cell.getY();
    }

    public ArrayList<Cell> getNextStateOverlay() {
        ArrayList<Cell> overlayCellList = new ArrayList<>();
        Map<String, List<Cell>> groupedOverlay = nextStateOverlay
                .stream()
                .collect(Collectors.groupingBy(this::getGroupingByKey, Collectors.mapping((Cell cell) -> cell, Collectors.toList())));

        groupedOverlay.forEach((key, cells) -> {
            int aliveCount = 0;
            int deadCount = 0;
            for (Cell cell: cells) {
                if (cell.getCellState().equals(CellState.ALIVE)) {
                    aliveCount++;
                } else {
                    deadCount++;
                }
            }
            if (aliveCount >= deadCount) {
                overlayCellList.add(new Cell(-1, cells.getFirst().getX(), cells.getFirst().getY(), CellState.ALIVE));
            } else {
                overlayCellList.add(new Cell(-1, cells.getFirst().getX(), cells.getFirst().getY(), CellState.ALIVE));
            }
        });
        return overlayCellList;
    }

    public void resetOverlay() {
        this.nextStateOverlay = new ArrayList<>();
    }
}
