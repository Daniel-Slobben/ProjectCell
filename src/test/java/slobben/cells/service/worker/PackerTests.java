package slobben.cells.service.worker;

import net.jpountz.lz4.LZ4Factory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ActiveProfiles;
import slobben.cells.dto.outgoing.EncodedBlock;
import slobben.cells.dto.outgoing.EncodedBlockType;
import slobben.cells.entities.Block;
import slobben.cells.service.workers.AlgorithmGol;
import slobben.cells.service.workers.Packer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static slobben.cells.service.worker.AlgorithmGolTests.draw;

@SpringBootTest(properties = {"cells.size.blockSize=70", "cells.size.sectorSize=10"})
@ComponentScan("slobben.cells.service")
@ActiveProfiles(profiles = "unit")
class PackerTests {

    @Autowired
    private AlgorithmGol algorithmGol;
    @Value("${cells.size.blockSize}")
    private int blockSize;

    @Test
    void packsTheInnerCells() {
        // prepare
        Block block = getActiveBlock();

        // execute
        Packer.packAll(block);

        // verify
        EncodedBlock full = block.getEncodedBlock();
        assertThat(full.x()).isEqualTo(3);
        assertThat(full.y()).isEqualTo(-2);
        assertThat(full.generation()).isEqualTo(1);
        assertThat(full.type()).isEqualTo(EncodedBlockType.FULL);

        byte[] bits = decompress(full);
        assertThat(bits).hasSize(613); // 70 * 70 bits
        assertThat(aliveBits(bits)).containsExactly(
                0, 1, // [1][1], [1][2]
                62, 63,       // [1][63], [1][64]
                70, 71,       // [2][1], [2][2]
                132, 133,     // [2][63], [2][64]
                4828, 4829,   // [69][69], [69][70]
                4898, 4899);  // [70][69], [70][70]
    }

    @Test
    void packsTheBorders() {
        // prepare
        Block block = getActiveBlock();

        // execute
        Packer.packAll(block);

        // verify
        EncodedBlock borders = block.getEncodedBlockBorders();
        assertThat(borders.generation()).isEqualTo(1);
        assertThat(borders.type()).isEqualTo(EncodedBlockType.BORDER);
        byte[] bits = decompress(borders);
        assertThat(bits).hasSize(35); // 70 * 4 - 4 bits
        assertThat(aliveBits(bits)).containsExactly(
                68, 69,        // [69][70], [70][70] in line [1..70][70]
                70, 71,        // [1][1], [2][1] in line [1..70][1], from bit 70
                140, 201, 202, // [1][2], [1][63], [1][64] in line [1][2..69], from bit 140
                275);          // [70][69] in line [70][2..69], from bit 208
    }

    @Test
    void newBlockIsSentInFull() {
        // prepare
        Block block = new Block(0, 0, UUID.randomUUID(), blockSize);

        // execute
        Packer.packAll(block);

        // verify
        assertThat(block.getEncodedBlockBorders()).isSameAs(block.getEncodedBlock());
        assertThat(block.getEncodedBlock().type()).isEqualTo(EncodedBlockType.FULL);
    }

    @Test
    void encodingsStayAtThePackedTick() {
        // prepare
        Block block = new Block(0, 0, UUID.randomUUID(), blockSize);
        draw(block, 10, 20, "###");
        algorithmGol.setNextState(block);

        // execute
        Packer.packAll(block);
        algorithmGol.setNextState(block);

        // verify
        EncodedBlock full = block.getEncodedBlock();
        assertThat(full.generation()).isEqualTo(1);
        assertThat(aliveBits(decompress(full))).containsExactly(580, 650, 720); // the vertical blinker [9..11][21]
    }

    private Block getActiveBlock() {
        Block block = new Block(3, -2, UUID.randomUUID(), blockSize);
        draw(block, 1, 1, "##\n##");
        draw(block, 1, 63, "##\n##");
        draw(block, 69, 69, "##\n##");
        draw(block, 0, 30, "#");
        draw(block, 71, 30, "#");
        draw(block, 69, 0, "#");
        draw(block, 1, 71, "#");
        algorithmGol.setNextState(block);
        return block;
    }

    private static byte[] decompress(EncodedBlock encoded) {
        return LZ4Factory.fastestInstance().safeDecompressor().decompress(encoded.encodedCells(), 1 << 20);
    }

    private static List<Integer> aliveBits(byte[] bits) {
        List<Integer> alive = new ArrayList<>();
        for (int i = 0; i < bits.length * 8; i++) {
            if ((bits[i / 8] >> (i % 8) & 1) == 1) alive.add(i);
        }
        return alive;
    }
}
