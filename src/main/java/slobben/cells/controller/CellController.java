package slobben.cells.controller;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import slobben.cells.config.EnvironmentConfig;
import slobben.cells.dto.incoming.ClientUpdateRequest;
import slobben.cells.dto.incoming.ReconnectRequest;
import slobben.cells.dto.outgoing.ChaosHitDto;
import slobben.cells.dto.outgoing.ReconnectResponse;
import slobben.cells.dto.outgoing.Settings;
import slobben.cells.dto.outgoing.StateInfo;
import slobben.cells.service.workers.ClientService;
import slobben.cells.service.workers.chaos.ChaosService;

import java.util.List;
import java.util.UUID;

@Controller
@AllArgsConstructor
@RequestMapping(value = {"/gen-api"})
public class CellController {

    private static final Logger log = LoggerFactory.getLogger(CellController.class);
    private final EnvironmentConfig environmentConfig;
    private final ChaosService chaosService;
    private final ClientService clientService;

    @GetMapping("settings")
    public ResponseEntity<Settings> getSettings() {
        UUID clientId = clientService.createNewClient();
        ChaosHitDto chaosHit = chaosService.getLatestHit().getDto();

        return ResponseEntity.ok(new Settings(environmentConfig.getBlockSize(), clientId, chaosHit));
    }

    @PostMapping("reconnect")
    public ResponseEntity<ReconnectResponse> postReconnect(@RequestBody ReconnectRequest reconnectRequest) {
        log.info("Reconnect request");
        UUID clientId = clientService.createNewClient();
        if (clientService.hasVisibleBlocks(reconnectRequest.visibleBlocks())) {
            return ResponseEntity.ok(new ReconnectResponse(clientId, null));
        }
        ChaosHitDto chaosHit = chaosService.getLatestHit().getDto();

        return ResponseEntity.ok(new ReconnectResponse(clientId, chaosHit));
    }

    @GetMapping("/next-chaos-hit/{hitId}/{getNext}")
    public ResponseEntity<ChaosHitDto> returnNextHit(@PathVariable UUID hitId, @PathVariable boolean getNext) {
        log.debug("Received request for next chaoshit. CurrentID {}, nextBoolean: {}", hitId, getNext);

        ChaosHitDto nextChaosHit = chaosService.getNextChaosHit(hitId, getNext);
        return ResponseEntity.ok(nextChaosHit);
    }

    @MessageMapping("/client-update")
    public void updateClient(@Payload ClientUpdateRequest message) {
        log.debug("Received update request for clientId: {}", message.client());

        clientService.updateClientBlocks(message);
        clientService.sendClientUpdate(message.client(), List.of(message.blocksToAdd()), false);
    }

    @MessageMapping("/block-request")
    public void getBlocks(@Payload ClientUpdateRequest message) {
        log.debug("Received block request for clientId: {}", message.client());
        clientService.addErrorClient(message.client());
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
