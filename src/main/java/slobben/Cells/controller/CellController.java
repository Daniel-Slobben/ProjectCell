package slobben.Cells.controller;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import slobben.Cells.config.StateInfo;
import slobben.Cells.service.EnvironmentService;
import slobben.Cells.service.RunnerService;

@Controller
@AllArgsConstructor
@RequestMapping(value = {"/gen-api"})
public class CellController {

    private static final Logger log = LoggerFactory.getLogger(CellController.class);
    private final RunnerService runnerService;
    private final EnvironmentService environmentService;

    @GetMapping("blocksize")
    public ResponseEntity<Integer> getBlockSize() {
        log.info("Received request for blocksize");
        return ResponseEntity.ok(environmentService.getBlockSize());
    }

    @PutMapping("block/{x}/{y}/set-block")
    public ResponseEntity<HttpStatus> setBlock(@PathVariable("x") int x, @PathVariable("y") int y, @RequestBody boolean[][] body) {
        log.info("Received request to set block x: {}, y: {}", x, y);
        runnerService.setBlock(x, y, body);
        return ResponseEntity.ok(HttpStatus.OK);
    }

    @GetMapping("state-info")
    public ResponseEntity<StateInfo> getStateInfo() {
        log.debug("Received request for state-info");
        return ResponseEntity.ok(runnerService.getStateInfo());
    }

}
