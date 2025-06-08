package slobben.Cells.controller;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import slobben.Cells.entities.model.Cell;
import slobben.Cells.service.EnvironmentService;
import slobben.Cells.service.RunnerService;

@Controller
@AllArgsConstructor
public class CellController {

    private static final Logger log = LoggerFactory.getLogger(CellController.class);
    private final RunnerService runnerService;
    private final EnvironmentService environmentService;

    @GetMapping("state/{x}/{y}")
    public ResponseEntity<Cell[][]> getBlock(@PathVariable("x") int x, @PathVariable("y") int y) {
        log.info("Received request for x: {}, y: {}", x, y);
        return ResponseEntity.ok(runnerService.getBlockWithoutBorders(x, y));
    }

    @GetMapping("blocksize")
    public ResponseEntity<Integer> getBlockSize() {
        return ResponseEntity.ok(environmentService.getBlockSize());
    }

//    @PutMapping("empty/block/{x}/{y}")
//    public ResponseEntity<HttpStatus> emptyBlock(@PathVariable("x") int x, @PathVariable("y") int y) {
//        //TODO write emtpy block
//        return ResponseEntity.ok(HttpStatus.ACCEPTED);
//    }

//    public void setBlock(Block blocksize)

//    @PutMapping("cell/{x}/{y}/toggle}")
//    public ResponseEntity<Void> setCell(@PathVariable("x") int x, @PathVariable("y") int y, CellState cellState) {
//        inputService.setCellInOverlay(new Cell(x, y));
//        return ResponseEntity.ok().build();
//    }
}
