package slobben.Cells.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import slobben.Cells.entities.model.Block;
import slobben.Cells.service.RunnerService;

import java.util.List;

@Controller
@RequiredArgsConstructor
@Slf4j
public class WebsocketController {

    private final RunnerService runnerService;

    @MessageMapping("/update-requested-blocks")
    public List<Block> send(ClientUpdateRequest message) {
        log.info("Received update request for clientId: {}", message.client());
        return runnerService.updateClient(message);
    }
}
