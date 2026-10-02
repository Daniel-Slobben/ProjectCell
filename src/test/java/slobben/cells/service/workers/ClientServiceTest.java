package slobben.cells.service.workers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import slobben.cells.config.EnvironmentConfig;
import slobben.cells.dto.incoming.ClientUpdateRequest;
import slobben.cells.entities.model.Block;
import slobben.cells.service.ExecutorService;
import slobben.cells.util.BlockUtils;

import java.util.Map;
import java.util.Random;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
@RequiredArgsConstructor
@Slf4j
class ClientServiceTest {

    @InjectMocks
    private ClientService clientService;
    @Mock
    private SimpMessagingTemplate simpMessagingTemplate;
    @Mock
    private ExecutorService executorService;
    @Mock
    private EnvironmentConfig environmentConfig;
    @Mock
    private Map<String, Block> blocks;

    @Test
    void updateClientBlocks() {
        // prepare
        UUID clientId = clientService.createNewClient();
        Random random = new Random();

        for (int i = 0; i < 1000; i++) {
            int topX = random.nextInt(-10, 0);
            int topY = random.nextInt(-10, 0);
            int botX = random.nextInt(0, 10);
            int botY = random.nextInt(0, 10);

            ClientUpdateRequest clientUpdateRequest = new ClientUpdateRequest(clientId,
                    BlockUtils.getKey(topX, topY),
                    BlockUtils.getKey(botX, botY));

            clientService.updateClientBlocks(clientUpdateRequest);
        }
    }
}