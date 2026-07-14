package slobben.cells.controller;

import jakarta.servlet.http.HttpSession;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import slobben.cells.config.EnvironmentConfig;
import slobben.cells.dto.ChaosHitDto;
import slobben.cells.dto.ClientUpdateRequest;
import slobben.cells.dto.Settings;
import slobben.cells.dto.StateInfo;
import slobben.cells.service.workers.ClientService;
import slobben.cells.service.workers.chaos.ChaosHit;
import slobben.cells.service.workers.chaos.ChaosService;

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
    public ResponseEntity<Settings> getSettings(HttpSession session) {
        UUID clientId = (UUID) session.getAttribute("clientId");

        if (clientId == null) {
            clientId = UUID.randomUUID();
            clientService.addClient(clientId);
            session.setAttribute("clientId", clientId);
        }

        ChaosHit chaosHit = chaosService.getLatestHit();

        return ResponseEntity.ok(new Settings(environmentConfig.getBlockSize(), clientId,
                new ChaosHitDto(chaosHit.getId(), chaosHit.getWorldX(), chaosHit.getWorldY(), chaosHit.getName(), chaosHit.getAge())));
    }

    @GetMapping("/next-chaos-hit/{hitId}/{getNext}")
    public ResponseEntity<ChaosHit> returnNextHit(@PathVariable UUID hitId, @PathVariable boolean getNext) {
        log.info("Received request for next chaoshit. CurrentID {}, nextBoolean: {}", hitId, getNext);

        ChaosHit nextChaosHit = chaosService.getNextChaosHit(hitId, getNext);
        return ResponseEntity.ok(nextChaosHit);
    }

    @MessageMapping("/client-update")
    public void updateClient(@Payload ClientUpdateRequest message) {
        log.debug("Received update request for clientId: {}", message.client());

        clientService.updateClientBlocks(message);
        clientService.updateClientWithId(message.client());
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
