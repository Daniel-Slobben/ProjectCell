package slobben.Cells.controller;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import slobben.Cells.database.model.Cell;
import slobben.Cells.enums.CellState;
import slobben.Cells.service.InputService;
import slobben.Cells.service.StateService;

import java.util.List;

@Controller
@AllArgsConstructor
public class CellController {

    private static final Logger log = LoggerFactory.getLogger(CellController.class);
    private final StateService stateService;
    private final InputService inputService;

    @GetMapping("state/{x}/{y}/{size}")
    public ResponseEntity<Cell[][]> getState(@PathVariable("x") int x, @PathVariable("y") int y, @PathVariable("size") int size) {
        log.info("Received request for x: {}, y: {} and size: {}", x, y, size);
        return ResponseEntity.ok(stateService.getMatrixState(x, y, size));
    }

//    @PutMapping("cell/{x}/{y}/toggle}")
//    public ResponseEntity<Void> setCell(@PathVariable("x") int x, @PathVariable("y") int y, CellState cellState) {
//        inputService.setCellInOverlay(new Cell(x, y));
//        return ResponseEntity.ok().build();
//    }
}
