package slobben.Cells.controller;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import slobben.Cells.config.BlockUpdate;
import slobben.Cells.config.StateInfo;
import slobben.Cells.service.EnvironmentService;
import slobben.Cells.service.RunnerService;

@Controller
@AllArgsConstructor
public class CellController {

    private static final Logger log = LoggerFactory.getLogger(CellController.class);
    private final RunnerService runnerService;
    private final EnvironmentService environmentService;

    @GetMapping("blocksize")
    public ResponseEntity<Integer> getBlockSize() {
        log.info("Received request for blocksize");
        return ResponseEntity.ok(environmentService.getBlockSize());
    }

    @GetMapping("block/{x}/{y}")
    public ResponseEntity<boolean[][]> toggleUpdate(@PathVariable("x") int x, @PathVariable("y") int y, @RequestParam boolean isUpdating) {
        log.debug("Received request to set update {} for block x: {}, y: {}", isUpdating, x, y);
        boolean[][] result = runnerService.setBlockUpdate(x, y, isUpdating);
        return ResponseEntity.ok(result);
    }

    @PutMapping("block/{x}/{y}/set-block")
    public ResponseEntity<HttpStatus> setBlock(@PathVariable("x") int x, @PathVariable("y") int y, @RequestBody boolean[][] body) {
        runnerService.getBlockUpdates().add(BlockUpdate.builder().x(x).y(y).state(body).build());
        return ResponseEntity.ok(HttpStatus.OK);
    }


    @GetMapping("state-info")
    public ResponseEntity<StateInfo> getStateInfo() {
        log.debug("Received request for state-info");
        return ResponseEntity.ok(runnerService.getStateInfo());
    }

}
