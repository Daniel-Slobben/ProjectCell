package slobben.cells.controller;

import jakarta.servlet.http.HttpSession;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.util.Pair;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import slobben.cells.config.EnvironmentConfig;
import slobben.cells.dto.ClientUpdateRequest;
import slobben.cells.dto.EncodedBlock;
import slobben.cells.dto.Settings;
import slobben.cells.dto.StateInfo;
import slobben.cells.service.RunnerService;
import slobben.cells.service.workers.ChaosService;
import slobben.cells.service.workers.ClientService;
import slobben.cells.service.workers.NewBlockService;

import java.util.List;
import java.util.UUID;

@Controller
@AllArgsConstructor
@RequestMapping(value = {"/gen-api"})
public class CellController {

    private static final Logger log = LoggerFactory.getLogger(CellController.class);
    private final NewBlockService newBlockService;
    private final RunnerService runnerService;
    private final EnvironmentConfig environmentConfig;
    private final ChaosService chaosService;
    private final ClientService clientService;

    @GetMapping("settings")
    public ResponseEntity<Settings> getSettings(HttpSession session) {
        UUID clientId = (UUID) session.getAttribute("clientId");

        if (clientId == null) {
            clientId = UUID.randomUUID();
            clientService.addClient(clientId);
            session.setAttribute("clientId", clientId);
        }

        Pair<Integer, Integer> initialXY = chaosService.getOneOfLatestHits();
        return ResponseEntity.ok(new Settings(environmentConfig.getBlockSize(), clientId, initialXY.getFirst(), initialXY.getSecond()));
    }

    @PutMapping("block/{x}/{y}/set-block")
    public ResponseEntity<HttpStatus> setBlock(@PathVariable int x, @PathVariable int y, @RequestBody boolean[][] body) {
        log.info("Received request to set block x: {}, y: {}", x, y);
        newBlockService.setBlock(x, y, body);
        return ResponseEntity.ok(HttpStatus.OK);
    }

    @PostMapping("/client-update")
    public ResponseEntity<List<EncodedBlock>> send(@RequestBody ClientUpdateRequest message) {
        log.debug("Received update request for clientId: {}", message.client());
        return ResponseEntity.ok(clientService.updateClient(message));
    }

    @GetMapping("/state-info")
    public ResponseEntity<StateInfo> getStateInfo() {
        log.debug("Received request for state-info");
        return ResponseEntity.ok(null);
    }

    @DeleteMapping("/disconnect/{clientId}")
    public ResponseEntity<HttpStatusCode> disconnect(@PathVariable UUID clientId) {
        log.debug("Received request to disconnect client with id: {}", clientId);
        clientService.disconnectClient(clientId);
        return ResponseEntity.ok().build();
    }

}
