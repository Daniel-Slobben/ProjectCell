package slobben.cells.service.worker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import slobben.cells.dto.internal.Coordinates;
import slobben.cells.service.RleReader;
import slobben.cells.service.WorldEditor;
import slobben.cells.service.workers.chaosmakers.SpaceshipMaker;
import slobben.cells.service.workers.chaosmakers.SpaceshipMaker.Direction;

import java.io.IOException;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

// every ship is written by the real RleReader and run long enough to see where it goes on screen
class SpaceshipDirectionsTest {
    private static final int GENERATIONS = 120;
    private static final Map<String, Direction> DIRECTIONS = SpaceshipMaker.readDirections();

    @Test
    void everySpaceshipHasADirection() throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources("classpath:patterns/" + SpaceshipMaker.DIR + "*.rle");
        List<String> names = Arrays.stream(resources).map(r -> Objects.requireNonNull(r.getFilename()).replace(".rle", "")).toList();

        assertThat(DIRECTIONS.keySet()).containsExactlyInAnyOrderElementsOf(names);
    }

    @ParameterizedTest
    @MethodSource("patterns")
    void spaceshipHeadsInItsDirection(String pattern) throws IOException {
        assertHeading(pattern, 0);
    }

    // rotating works the same for every ship, so one orthogonal and one diagonal ship cover it
    @ParameterizedTest
    @ValueSource(strings = {"dart", "crane"})
    void rotatedSpaceshipHeadsInItsRotatedDirection(String pattern) throws IOException {
        for (int rotation = 1; rotation < 4; rotation++) {
            assertHeading(pattern, rotation);
        }
    }

    private static void assertHeading(String pattern, int rotation) throws IOException {
        // prepare
        Set<Long> cells = new HashSet<>();
        WorldEditor worldEditor = mock(WorldEditor.class);
        doAnswer(invocation -> cells.add(key(invocation.getArgument(0), invocation.getArgument(1))))
                .when(worldEditor).setCell(anyInt(), anyInt(), any());
        new RleReader(worldEditor).writePatternToWorld(SpaceshipMaker.DIR + pattern, new Coordinates(0, 0), UUID.randomUUID(), (byte) rotation);
        int[] start = bounds(cells);

        // execute
        Set<Long> end = cells;
        for (int i = 0; i < GENERATIONS; i++) {
            end = step(end);
        }

        // verify
        int[] moved = bounds(end);
        int dx = leadingEdge(moved[0] - start[0], moved[1] - start[1]);
        int dy = leadingEdge(moved[2] - start[2], moved[3] - start[3]);
        Direction expected = DIRECTIONS.get(pattern).rotated(rotation);
        assertThat(heading(dx, dy)).as("%s at rotation %s moved (%s, %s)", pattern, rotation, dx, dy).isEqualTo(expected);
    }

    static Set<String> patterns() {
        return DIRECTIONS.keySet();
    }

    // a ship can shed or grow cells behind it, so it is followed by the edge that moved furthest
    private static int leadingEdge(int minMoved, int maxMoved) {
        return Math.abs(minMoved) > Math.abs(maxMoved) ? minMoved : maxMoved;
    }

    // the sign of each axis that moved at least half as far as the main axis
    private static Direction heading(int dx, int dy) {
        int main = Math.max(Math.abs(dx), Math.abs(dy));
        int sx = Math.abs(dx) * 2 >= main ? Integer.signum(dx) : 0;
        int sy = Math.abs(dy) * 2 >= main ? Integer.signum(dy) : 0;
        return Arrays.stream(Direction.values()).filter(d -> d.dx == sx && d.dy == sy).findFirst().orElseThrow();
    }

    private static int[] bounds(Set<Long> cells) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (long cell : cells) {
            minX = Math.min(minX, x(cell));
            maxX = Math.max(maxX, x(cell));
            minY = Math.min(minY, y(cell));
            maxY = Math.max(maxY, y(cell));
        }
        return new int[]{minX, maxX, minY, maxY};
    }

    private static Set<Long> step(Set<Long> cells) {
        Map<Long, Integer> neighbours = new HashMap<>();
        for (long cell : cells) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx != 0 || dy != 0) {
                        neighbours.merge(key(x(cell) + dx, y(cell) + dy), 1, Integer::sum);
                    }
                }
            }
        }
        Set<Long> next = new HashSet<>();
        neighbours.forEach((cell, n) -> {
            if (n == 3 || (n == 2 && cells.contains(cell))) {
                next.add(cell);
            }
        });
        return next;
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xffffffffL);
    }

    private static int x(long key) {
        return (int) (key >> 32);
    }

    private static int y(long key) {
        return (int) key;
    }
}
