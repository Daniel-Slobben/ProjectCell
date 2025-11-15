package slobben.Cells.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import slobben.Cells.service.RunnerService;

@Controller
@RequiredArgsConstructor
@Slf4j
public class WebsocketController {

    private final RunnerService runnerService;

    @MessageMapping("/update-requested-blocks")
    public void send(ClientUpdateRequest message) {
        log.info("Received update request for clientId: {}", message.client());
        runnerService.updateClient(message);
    }
}
