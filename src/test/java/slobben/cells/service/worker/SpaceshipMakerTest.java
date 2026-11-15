package slobben.cells.service.worker;

import org.junit.jupiter.api.RepeatedTest;
import slobben.cells.dto.internal.Coordinates;
import slobben.cells.entities.ChaosHit;
import slobben.cells.service.RleReader;
import slobben.cells.service.workers.chaosmakers.SpaceshipMaker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// the reader is mocked so every ship is a square of SHIP_SIZE written from its corner
class SpaceshipMakerTest {
    private static final int SHIP_SIZE = 50;

    @RepeatedTest(20)
    void writesThreeToTenShipsThatDoNotOverlap() throws IOException {
        // prepare
        RleReader rleReader = mock(RleReader.class);
        List<Coordinates> corners = new ArrayList<>();
        when(rleReader.writePatternToWorld(anyString(), any(Coordinates.class), any(UUID.class), anyByte())).thenAnswer(invocation -> {
            corners.add(invocation.getArgument(1));
            return SHIP_SIZE;
        });

        // execute
        ChaosHit hit = new SpaceshipMaker(rleReader).getChaosHit(100, 200, 2000);

        // verify
        assertThat(corners).hasSizeBetween(3, 10);
        for (int i = 1; i < corners.size(); i++) {
            assertThat(corners.get(i).x()).isGreaterThan(corners.get(i - 1).x() + SHIP_SIZE);
            assertThat(corners.get(i).y()).isEqualTo(200);
        }
        assertThat(hit.getCurrentStartingView().y()).isEqualTo(200 + SHIP_SIZE / 2);
    }
}
