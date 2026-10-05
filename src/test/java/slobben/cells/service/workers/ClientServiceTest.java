package slobben.cells.service.workers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import slobben.cells.config.EnvironmentConfig;
import slobben.cells.entities.model.Block;
import slobben.cells.service.ExecutorService;

import java.util.Map;

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
}