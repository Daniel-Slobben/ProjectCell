package slobben.Cells.controller;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import slobben.Cells.service.EnvironmentService;
import slobben.Cells.service.RunnerService;

import java.util.HashMap;

@Controller
@AllArgsConstructor
public class CellController {

    private static final Logger log = LoggerFactory.getLogger(CellController.class);
    private final RunnerService runnerService;
    private final EnvironmentService environmentService;

    @GetMapping("state/{x}/{y}")
    @CrossOrigin(origins = "http://localhost:4200")
    public ResponseEntity<Integer[][]> getBlock(@PathVariable("x") int x, @PathVariable("y") int y) {
        log.info("Received request for x: {}, y: {}", x, y);
        return ResponseEntity.ok(runnerService.getBlockWithoutBorders(x, y));
    }

    @GetMapping("blocksize")
    public ResponseEntity<Integer> getBlockSize() {
        log.info("Received request for blocksize");
        return ResponseEntity.ok(environmentService.getBlockSize());
    }

    @PutMapping("block/{x}/{y}/set")
    public ResponseEntity<HttpStatus> emptyBlock(@PathVariable("x") int x, @PathVariable("y") int y, HashMap<String, String> cells) {

        return ResponseEntity.ok(HttpStatus.ACCEPTED);
    }


//    public void setBlock(Block blocksize)

//    @PutMapping("cell/{x}/{y}/toggle}")
//    public ResponseEntity<Void> setCell(@PathVariable("x") int x, @PathVariable("y") int y, CellState cellState) {
//        inputService.setCellInOverlay(new Cell(x, y));
//        return ResponseEntity.ok().build();
//    }
}
