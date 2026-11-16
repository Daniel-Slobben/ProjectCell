package slobben.cells.service.worker;

import com.github.luben.zstd.Zstd;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import slobben.cells.dto.outgoing.EncodedBlock;
import slobben.cells.dto.outgoing.EncodedBlockType;
import slobben.cells.entities.Block;
import slobben.cells.service.workers.AlgorithmGol;
import slobben.cells.service.workers.Packer;
import slobben.cells.util.PackedRows;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThat;
import static slobben.cells.TestUtils.*;

@SpringBootTest(properties = {"cells.size.blockSize=70", "cells.size.sectorSize=10"})
@ComponentScan("slobben.cells.service")
@ActiveProfiles(profiles = "unit")
class PackerTest {

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

        byte[] bits = decodeFull(full.encodedCells(), blockSize);
        assertThat(bits).hasSize(613); // 70 * 70 bits
        assertThat(getAliveBitIndexes(bits)).containsExactly(
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
        assertThat(getAliveBitIndexes(bits)).containsExactly(
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
        drawPattern(block, 10, 20, "###");
        nextState(algorithmGol, block);

        // execute
        Packer.packAll(block);
        nextState(algorithmGol, block);

        // verify
        EncodedBlock full = block.getEncodedBlock();
        assertThat(full.generation()).isEqualTo(1);
        assertThat(getAliveBitIndexes(decodeFull(full.encodedCells(), blockSize))).containsExactly(580, 650, 720); // the vertical blinker [9..11][21]
    }

    @Test
    void tickPacksTheOtherBlocksWhileOneIsBeingEncoded() throws InterruptedException {
        // prepare: a tick, then the snapshot of one block held, as by a client encoding it
        Block busy = new Block(0, 0, UUID.randomUUID(), blockSize), free = new Block(1, 0, UUID.randomUUID(), blockSize);
        Map<String, Block> blocks = new LinkedHashMap<>();
        blocks.put("0/0", busy);
        blocks.put("1/0", free);
        Packer packer = new Packer(blocks);
        packer.execute();
        Map<?, ?> snapshots = (Map<?, ?>) ReflectionTestUtils.getField(packer, "snapshots");
        ReentrantLock lock = (ReentrantLock) ReflectionTestUtils.getField(snapshots.get(busy), "lock");
        Object busyBefore = encoding(busy), freeBefore = encoding(free);
        lock.lock();

        // execute
        // a daemon, so a failed check does not leave it waiting on the lock
        Thread tick = Thread.ofPlatform().daemon().start(packer::execute);
        for (int i = 0; i < 100 && encoding(free) == freeBefore; i++) Thread.sleep(10);

        // verify: the free block is packed and the tick waits for the busy one, which it packs once released
        assertThat(encoding(free)).isNotSameAs(freeBefore);
        assertThat(encoding(busy)).isSameAs(busyBefore);
        assertThat(tick.isAlive()).isTrue();
        lock.unlock();
        tick.join(1000);
        assertThat(encoding(busy)).isNotSameAs(busyBefore);
    }

    private static Object encoding(Block block) {
        return ReflectionTestUtils.getField(block, "encodedBlock");
    }

    @ParameterizedTest
    @CsvSource({"2000, 0", "2000, 0.002", "2000, 0.03", "2000, 0.5", "2000, 1", "500, 0.1", "61, 1", "10, 0.5", "1, 1"})
    void fullBlockDecodesToItsCells(int size, double density) {
        // prepare: random cells, and live cells all around the block, which are not part of it
        Random random = new Random(size + (long) (density * 1000));
        Block block = new Block(0, 0, UUID.randomUUID(), size);
        long[][] rows = block.getPackedCells();
        byte[] expected = new byte[(size * size + 7) / 8];
        for (int x = 0; x <= size + 1; x++) {
            for (int y = 0; y <= size + 1; y++) {
                boolean inner = x >= 1 && x <= size && y >= 1 && y <= size;
                if (inner && random.nextDouble() >= density) continue;
                PackedRows.set(rows[x], y);
                if (inner) setBit(expected, (x - 1) * size + y - 1);
            }
        }

        // execute
        Packer.packAll(block);

        // verify
        assertThat(decodeFull(block.getEncodedBlock().encodedCells(), size)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"2000, 40", "130, 7", "61, 3", "64, 1"})
    void fullBlockWithRepeatedRowsDecodesToItsCells(int size, int differentRows) {
        // prepare: every row is one of a few random rows, or empty
        Random random = new Random(size);
        boolean[][] pool = new boolean[differentRows + 1][size];
        for (int i = 1; i <= differentRows; i++) {
            for (int y = 0; y < size; y++) pool[i][y] = random.nextInt(4) == 0;
        }
        Block block = new Block(0, 0, UUID.randomUUID(), size);
        byte[] expected = new byte[(size * size + 7) / 8];
        for (int x = 0; x < size; x++) {
            boolean[] row = pool[random.nextInt(pool.length)];
            for (int y = 0; y < size; y++) {
                if (!row[y]) continue;
                PackedRows.set(block.getPackedCells()[x + 1], y + 1);
                setBit(expected, x * size + y);
            }
        }

        // execute
        Packer.packAll(block);

        // verify
        assertThat(decodeFull(block.getEncodedBlock().encodedCells(), size)).isEqualTo(expected);
    }

    private Block getActiveBlock() {
        Block block = new Block(3, -2, UUID.randomUUID(), blockSize);
        drawPattern(block, 1, 1, "##\n##");
        drawPattern(block, 1, 63, "##\n##");
        drawPattern(block, 69, 69, "##\n##");
        drawPattern(block, 0, 30, "#");
        drawPattern(block, 71, 30, "#");
        drawPattern(block, 69, 0, "#");
        drawPattern(block, 1, 71, "#");
        nextState(algorithmGol, block);
        return block;
    }

    private static byte[] decompress(EncodedBlock encoded) {
        return Zstd.decompress(encoded.encodedCells(), (int) Zstd.getFrameContentSize(encoded.encodedCells()));
    }

    private static List<Integer> getAliveBitIndexes(byte[] bits) {
        List<Integer> alive = new ArrayList<>();
        for (int i = 0; i < bits.length * 8; i++) {
            if ((bits[i / 8] >> (i % 8) & 1) == 1) alive.add(i);
        }
        return alive;
    }
}
