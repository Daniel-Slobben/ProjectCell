package slobben.Cells.service;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import slobben.Cells.entities.model.Block;
import slobben.Cells.entities.model.EncodedBlock;

import java.util.*;

@Service
@AllArgsConstructor
public class UpdateWebService {

    private final SimpMessagingTemplate simpMessagingTemplate;
    private final BoardInfoService boardInfoService;

    public void updateClient(UUID uuid, Queue<Block> blocks) {
        var copyOfBlocks = List.copyOf(blocks).stream()
                .map(boardInfoService::getBlockWithoutBorder)
                .map(Block::getEncodedBlock).toList();

        String topic = String.format("/topic/" + uuid);
        simpMessagingTemplate.convertAndSend(topic, copyOfBlocks);
    }
}
