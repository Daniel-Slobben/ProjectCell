package slobben.cells.controller;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.util.Pair;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import slobben.cells.config.StateInfo;
import slobben.cells.service.ChaosService;
import slobben.cells.service.EnvironmentService;
import slobben.cells.service.RunnerService;

import java.util.List;

@Controller
@AllArgsConstructor
@RequestMapping(value = {"/gen-api"})
public class CellController {

    private static final Logger log = LoggerFactory.getLogger(CellController.class);
    private final RunnerService runnerService;
    private final EnvironmentService environmentService;
    private final ChaosService chaosService;

    @GetMapping("settings")
    public ResponseEntity<Settings> getSettings() {
        log.info("Received request for settings");
        Pair<Integer, Integer> initialXY = chaosService.getOneOfLatestHits();
        return ResponseEntity.ok(new Settings(environmentService.getBlockSize(), initialXY.getFirst(), initialXY.getSecond()));
    }

    @PutMapping("block/{x}/{y}/set-block")
    public ResponseEntity<HttpStatus> setBlock(@PathVariable("x") int x, @PathVariable("y") int y, @RequestBody boolean[][] body) {
        log.info("Received request to set block x: {}, y: {}", x, y);
        runnerService.setBlock(x, y, body);
        return ResponseEntity.ok(HttpStatus.OK);
    }

    @PostMapping("/client-update")
    public ResponseEntity<List<EncodedBlock>> send(@RequestBody ClientUpdateRequest message) {
        log.info("Received update request for clientId: {}", message.client());
        return ResponseEntity.ok(runnerService.updateClient(message));
    }

    @GetMapping("/state-info")
    public ResponseEntity<StateInfo> getStateInfo() {
        log.debug("Received request for state-info");
        return ResponseEntity.ok(runnerService.getStateInfo());
    }

}
