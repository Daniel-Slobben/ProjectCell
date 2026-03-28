package slobben.cells.service;

import lombok.AllArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import slobben.cells.entities.model.Block;

import java.util.*;

@Service
@AllArgsConstructor
public class UpdateWebService {

    private final SimpMessagingTemplate simpMessagingTemplate;

    public void updateClient(UUID uuid, Queue<Block> blocks) {
        var copyOfBlocks = List.copyOf(blocks).stream()
                .map(Block::getEncodedBlock).toList();

        simpMessagingTemplate.convertAndSend("/topic/%s".formatted(uuid), copyOfBlocks);
    }
}
