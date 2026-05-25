package slobben.cells;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@ComponentScan({"slobben.cells.service", "slobben.cells.config"})
@ActiveProfiles(profiles = "unit")
public class RecordingServiceTest {
}
