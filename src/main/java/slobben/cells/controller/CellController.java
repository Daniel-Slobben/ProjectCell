package slobben.cells.controller;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import slobben.cells.dto.incoming.ClientUpdateRequest;
import slobben.cells.dto.internal.Coordinates;
import slobben.cells.dto.outgoing.Settings;
import slobben.cells.dto.outgoing.StateInfo;
import slobben.cells.service.workers.ClientService;
import slobben.cells.service.workers.ViewportService;

import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping(value = {"/gen-api"})
public class CellController {

    private static final Logger log = LoggerFactory.getLogger(CellController.class);
    private final ViewportService viewportService;
    private final ClientService clientService;

    @Value("${cells.size.blockSize}")
    private int blockSize;

    @Value("${cells.targetspeed}")
    private int targetSpeed;


    @GetMapping("settings")
    public ResponseEntity<Settings> getSettings() {
        log.debug("Received settings request");
        Coordinates viewport = viewportService.getInterestingViewport(List.of());

        return ResponseEntity.ok(new Settings(blockSize, viewport, targetSpeed));
    }

    @PostMapping("viewport")
    public ResponseEntity<Coordinates> getNewViewport(@RequestBody List<Coordinates> recentViewports) {
        return ResponseEntity.ok(viewportService.getInterestingViewport(recentViewports));
    }

    @MessageMapping("/client-update")
    public void updateClient(@Payload ClientUpdateRequest message, @Header("simpSessionId") String sessionId) {
        log.debug("Received update request for session: {}", sessionId);

        clientService.updateClientBlocks(sessionId, message);
    }

    @GetMapping("/state-info")
    public ResponseEntity<StateInfo> getStateInfo() {
        log.debug("Received request for state-info");
        return ResponseEntity.ok(null);
    }
}
