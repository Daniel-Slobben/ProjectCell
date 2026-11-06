package slobben.cells.controller;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import slobben.cells.dto.incoming.ClientUpdateRequest;
import slobben.cells.dto.incoming.ReconnectRequest;
import slobben.cells.dto.internal.Coordinates;
import slobben.cells.dto.outgoing.ReconnectResponse;
import slobben.cells.dto.outgoing.Settings;
import slobben.cells.dto.outgoing.StateInfo;
import slobben.cells.service.workers.ClientService;
import slobben.cells.service.workers.ViewportService;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
@RequestMapping(value = {"/gen-api"})
public class CellController {

    private static final Logger log = LoggerFactory.getLogger(CellController.class);
    private final ViewportService viewportService;
    private final ClientService clientService;

    @Value("${cells.size.blockSize}")
    private int blockSize;


    @GetMapping("settings")
    public ResponseEntity<Settings> getSettings() {
        UUID clientId = clientService.createNewClient();

        log.debug("Received settings request. Assigning clientId {}", clientId);
        Coordinates viewport = viewportService.getInterestingViewport();

        return ResponseEntity.ok(new Settings(blockSize, clientId, viewport, clientService.getInitialBlocks(viewport.x(), viewport.y())));
    }

    @PostMapping("reconnect")
    public ResponseEntity<ReconnectResponse> postReconnect(@RequestBody ReconnectRequest reconnectRequest) {
        UUID clientId = clientService.createNewClient();

        log.debug("Reconnect request");
        if (clientService.hasVisibleBlocks(reconnectRequest.visibleBlocks())) {
            return ResponseEntity.ok(new ReconnectResponse(clientId, null));
        }
        Coordinates viewport = viewportService.getInterestingViewport();

        return ResponseEntity.ok(new ReconnectResponse(clientId, viewport));
    }

    @GetMapping("viewport")
    public ResponseEntity<Coordinates> getNewViewport() {
        return ResponseEntity.ok(viewportService.getInterestingViewport());
    }

    @MessageMapping("/client-update")
    public void updateClient(@Payload ClientUpdateRequest message) {
        log.debug("Received update request for clientId: {}", message.client());

        clientService.updateClientBlocks(message);
    }

    @MessageMapping("/health-check")
    public void getBlocks(@Payload UUID clientId) {
        log.debug("Received health-check for clientId: {}", clientId);
        clientService.healthCheck(clientId);
    }

    @GetMapping("/state-info")
    public ResponseEntity<StateInfo> getStateInfo() {
        log.debug("Received request for state-info");
        return ResponseEntity.ok(null);
    }
}
