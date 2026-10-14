package slobben.cells.service.worker;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ActiveProfiles;
import slobben.cells.entities.Block;
import slobben.cells.enums.BlockState;
import slobben.cells.service.workers.AlgorithmGol;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"cells.size.blockSize=70", "cells.size.sectorSize=10"})
@ComponentScan("slobben.cells.service")
@ActiveProfiles(profiles = "unit")
class AlgorithmGolTests {

    @Autowired
    private AlgorithmGol algorithmGol;
    @Value("${cells.size.blockSize}")
    private int blockSize;

    @Test
    void squaresStayAlive() {
        // prepare
        Block block = block();
        draw(block, 1, 1, "##\n##");
        draw(block, 30, 63, "##\n##");

        // execute
        algorithmGol.setNextState(block);

        // verify
        assertThat(window(block, 1, 1, 2, 2)).isEqualTo("##\n##\n");
        assertThat(window(block, 30, 63, 2, 2)).isEqualTo("##\n##\n");
        assertThat(aliveCount(block)).isEqualTo(8);
        assertThat(block.getGeneration()).isEqualTo(1);
        assertThat(block.getBlockState()).isEqualTo(BlockState.ACTIVE);
    }

    @Test
    void blinkerOscillates() {
        // prepare
        Block block = block();
        draw(block, 20, 63, "###");

        // execute
        algorithmGol.setNextState(block);

        // verify
        assertThat(window(block, 19, 63, 3, 3)).isEqualTo("""
                .#.
                .#.
                .#.
                """);
        assertThat(aliveCount(block)).isEqualTo(3);
    }

    @Test
    void gliderMoves() {
        // prepare
        Block block = block();
        draw(block, 10, 61, """
                .#.
                ..#
                ###
                """);

        // execute
        for (int generation = 0; generation < 4; generation++) {
            algorithmGol.setNextState(block);
        }

        // verify
        assertThat(window(block, 10, 61, 4, 4)).isEqualTo("""
                ....
                ..#.
                ...#
                .###
                """);
        assertThat(aliveCount(block)).isEqualTo(5);
    }

    @Test
    void bordersAreNeighbours() {
        // prepare
        Block block = block();
        draw(block, 0, 10, "####");
        draw(block, 71, 10, "####");
        draw(block, 10, 0, "#\n#\n#\n#");
        draw(block, 10, 71, "#\n#\n#\n#");

        // execute
        algorithmGol.setNextState(block);

        // verify
        // the border cells themselves are left alone, Stitching owns them
        assertThat(window(block, 0, 10, 2, 4)).isEqualTo("""
                ####
                .##.
                """);
        assertThat(window(block, 70, 10, 2, 4)).isEqualTo("""
                .##.
                ####
                """);
        assertThat(window(block, 10, 0, 4, 2)).isEqualTo("""
                #.
                ##
                ##
                #.
                """);
        assertThat(window(block, 10, 70, 4, 2)).isEqualTo("""
                .#
                ##
                ##
                .#
                """);
        assertThat(aliveCount(block)).isEqualTo(16 + 8);
    }

    @Test
    void sectorCountsFollowTheCells() {
        // prepare
        Block block = block();
        draw(block, 20, 30, "###");
        draw(block, 50, 50, "#");

        // expected sector counts after gol run
        short[][] expectedSector = new short[7][7];
        expectedSector[1][3] = 2;
        expectedSector[2][3] = 1;

        // execute
        algorithmGol.setNextState(block);

        // verify
        assertThat(block.getSectorCounts()).isDeepEqualTo(expectedSector);
    }

    @Test
    void newCellsArrayIsUsed() {
        // prepare
        Block block = block();
        draw(block, 20, 20, "###");

        Block replacement = block();
        draw(replacement, 40, 40, "##\n##");

        // execute
        algorithmGol.setNextState(block);
        block.setCells(replacement.getCells());
        block.setSectorCounts(null);
        algorithmGol.setNextState(block);

        // verify
        assertThat(window(block, 40, 40, 2, 2)).isEqualTo("##\n##\n");
        assertThat(aliveCount(block)).isEqualTo(4);
    }

    private Block block() {
        return new Block(0, 0, UUID.randomUUID(), blockSize);
    }

    static void draw(Block block, int x, int y, String pattern) {
        List<String> lines = pattern.lines().toList();
        for (int i = 0; i < lines.size(); i++) {
            for (int j = 0; j < lines.get(i).length(); j++) {
                block.getCells()[x + i][y + j] = lines.get(i).charAt(j) == '#';
            }
        }
    }

    private static String window(Block block, int x, int y, int height, int width) {
        StringBuilder window = new StringBuilder();
        for (int i = x; i < x + height; i++) {
            for (int j = y; j < y + width; j++) {
                window.append(block.getCells()[i][j] ? '#' : '.');
            }
            window.append('\n');
        }
        return window.toString();
    }

    private static int aliveCount(Block block) {
        int alive = 0;
        for (boolean[] row : block.getCells()) {
            for (boolean cell : row) {
                if (cell) alive++;
            }
        }
        return alive;
    }
}
