package slobben.Cells.service;

import lombok.AllArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import slobben.Cells.entities.model.Block;

@Service
@AllArgsConstructor
public class UpdateWebService {

    private final SimpMessagingTemplate simpMessagingTemplate;
    private final BoardInfoService boardInfoService;

    public void updateBlock(Block block) {
        String topic = String.format("/topic/block/%s/%s", block.getX(), block.getY());
        simpMessagingTemplate.convertAndSend(topic, boardInfoService.getBlockWithoutBorder(block));
    }
}
