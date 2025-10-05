package slobben.Cells.controller;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import slobben.Cells.config.StateInfo;
import slobben.Cells.service.EnvironmentService;
import slobben.Cells.service.RunnerService;

@Controller
@AllArgsConstructor
public class CellController {

    private static final Logger log = LoggerFactory.getLogger(CellController.class);
    private final RunnerService runnerService;
    private final EnvironmentService environmentService;

    @GetMapping("block/{x}/{y}/state")
    @CrossOrigin(origins = "http://localhost:4200")
    public ResponseEntity<boolean[][]> getBlock(@PathVariable("x") int x, @PathVariable("y") int y) {
        log.info("Received request for x: {}, y: {}", x, y);
        return ResponseEntity.ok(runnerService.getBlockWithoutBorders(x, y));
    }

    @GetMapping("blocksize")
    public ResponseEntity<Integer> getBlockSize() {
        log.info("Received request for blocksize");
        return ResponseEntity.ok(environmentService.getBlockSize());
    }

    // Returns the original value of the block
    @GetMapping("block/{x}/{y}")
    public ResponseEntity<Boolean> toggleUpdate(@PathVariable("x") int x, @PathVariable("y") int y,
                                                @RequestParam boolean isUpdating) {
        log.debug("Received request to set update {} for block x: {}, y: {}", isUpdating, x, y);
        boolean result = runnerService.setBlockUpdate(x, y, isUpdating);
        return ResponseEntity.ok(result);
    }

    @GetMapping("state-info")
    public ResponseEntity<StateInfo> getStateInfo() {
        log.debug("Received request for state-info");
        return ResponseEntity.ok(runnerService.getStateInfo());
    }

}
