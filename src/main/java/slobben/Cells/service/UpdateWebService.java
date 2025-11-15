package slobben.Cells.service;

import lombok.AllArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import slobben.Cells.entities.model.Block;

import java.util.Set;
import java.util.UUID;

@Service
@AllArgsConstructor
public class UpdateWebService {

    private final SimpMessagingTemplate simpMessagingTemplate;
    private final BoardInfoService boardInfoService;

    public void updateClient(UUID uuid, Set<Block> blocks) {
        String topic = String.format("/topic/" + uuid, blocks);
        simpMessagingTemplate.convertAndSend(topic, blocks);
    }
}
