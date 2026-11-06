package slobben.cells.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.web.bind.annotation.ControllerAdvice;
import slobben.cells.exceptions.NotAClientException;

@ControllerAdvice("slobben.cells.controller")
@Slf4j
public class CellAdvice {

    @MessageExceptionHandler(NotAClientException.class)
    public void handleNotAClient(NotAClientException exception) {
        log.debug(exception.getMessage());
    }
}
