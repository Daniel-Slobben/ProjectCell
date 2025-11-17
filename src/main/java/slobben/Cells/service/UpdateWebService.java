package slobben.Cells.service;

import lombok.AllArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import slobben.Cells.entities.model.Block;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@AllArgsConstructor
public class UpdateWebService {

    private final SimpMessagingTemplate simpMessagingTemplate;
    private final BoardInfoService boardInfoService;

    public void updateClient(UUID uuid, Set<Block> blocks) {
        var copyOfBlocks = List.copyOf(blocks).stream().map(boardInfoService::getBlockWithoutBorder).toList();
        String topic = String.format("/topic/" + uuid);
        simpMessagingTemplate.convertAndSend(topic, copyOfBlocks);
    }
}
