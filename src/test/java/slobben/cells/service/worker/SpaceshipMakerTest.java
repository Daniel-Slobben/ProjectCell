package slobben.cells.service.worker;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import slobben.cells.dto.internal.Coordinates;
import slobben.cells.entities.ChaosHit;
import slobben.cells.service.RleReader;
import slobben.cells.service.workers.chaosmakers.SpaceshipMaker;
import slobben.cells.enums.Direction;

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
    private static final int HIT_SIZE = 4000;

    @RepeatedTest(20)
    void writesAVOfShipsThatDoNotOverlap() throws IOException {
        // prepare
        List<String> patterns = new ArrayList<>();
        List<Coordinates> corners = new ArrayList<>();
        RleReader rleReader = recordingReader(patterns, corners);

        // execute
        ChaosHit hit = new SpaceshipMaker(rleReader).getChaosHit(100, 200, HIT_SIZE);

        // verify
        assertThat(corners).hasSizeGreaterThan(1);
        assertThat(patterns).containsOnly(patterns.getFirst());
        assertThat(hit.getCurrentStartingView()).isEqualTo(new Coordinates(100, 200));
        for (int i = 0; i < corners.size(); i++) {
            Coordinates a = corners.get(i);
            assertThat(Math.abs(a.x() - 100)).isLessThanOrEqualTo(HIT_SIZE / 2);
            assertThat(Math.abs(a.y() - 200)).isLessThanOrEqualTo(HIT_SIZE / 2);
            for (int j = i + 1; j < corners.size(); j++) {
                Coordinates b = corners.get(j);
                assertThat(Math.abs(a.x() - b.x()) > SHIP_SIZE || Math.abs(a.y() - b.y()) > SHIP_SIZE).as("ships %s and %s overlap", a, b).isTrue();
            }
        }
    }

    @Test
    void capsTheFleetAtFiveThousandCells() throws IOException {
        // prepare
        List<Coordinates> corners = new ArrayList<>();
        RleReader rleReader = recordingReader(new ArrayList<>(), corners);

        // execute
        new SpaceshipMaker(rleReader).getChaosHit(0, 0, 16_000);

        // verify
        assertThat(corners).allSatisfy(corner -> {
            assertThat(Math.abs(corner.x())).isLessThanOrEqualTo(2500);
            assertThat(Math.abs(corner.y())).isLessThanOrEqualTo(2500);
        });
    }

    @RepeatedTest(20)
    void aimsAtTheMiddleOfTheMap() throws IOException {
        // prepare
        RleReader rleReader = recordingReader(new ArrayList<>(), new ArrayList<>());

        // execute
        ChaosHit hit = new SpaceshipMaker(rleReader).getChaosHit(-50_000, 10, HIT_SIZE);

        // verify
        String heading = hit.getName().substring(hit.getName().lastIndexOf(' ') + 1);
        assertThat(Direction.valueOf(heading).getDx()).as(hit.getName()).isEqualTo(1);
    }

    private static RleReader recordingReader(List<String> patterns, List<Coordinates> corners) throws IOException {
        RleReader rleReader = mock(RleReader.class);
        when(rleReader.writePatternToWorld(anyString(), any(Coordinates.class), any(UUID.class), anyByte())).thenAnswer(invocation -> {
            patterns.add(invocation.getArgument(0));
            corners.add(invocation.getArgument(1));
            return SHIP_SIZE;
        });
        return rleReader;
    }
}
