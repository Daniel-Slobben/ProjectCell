package slobben.cells.service.worker;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.DefaultSubscriptionRegistry;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.support.AbstractSubscribableChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import slobben.cells.dto.incoming.ClientUpdateRequest;
import slobben.cells.dto.outgoing.EncodedBlock;
import slobben.cells.dto.outgoing.EncodedBlockType;
import slobben.cells.entities.Block;
import slobben.cells.service.workers.AlgorithmGol;
import slobben.cells.service.workers.ClientService;
import slobben.cells.service.workers.Packer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static slobben.cells.TestUtils.createAlgorithmGol;
import static slobben.cells.TestUtils.nextState;

/**
 * Times ClientService's tick with many STOMP sessions on the real broker, without websockets: each client is the
 * CONNECT and SUBSCRIBE a browser sends, and the outbound channel only counts the messages the sessions would get.
 * "tick" is ClientService.execute, "drained" is until the outbound channel has handed over every message.
 * <p>
 * mvn test -Dtest=ClientUpdateBench -Djunit.jupiter.conditions.deactivate='org.junit.*DisabledCondition',
 * with -Dbench.clients=2500 -Dbench.ticks=200
 * <p>
 * realBlocksTick: the tick as in production, ticks of 500 ms in a world of 64 x 64 blocks of 2000 x 2000 cells, each
 * with the cells of one of 48 blocks of random cells after 1 to 1000 generations: chaos as it ages. The blocks are
 * packed every tick as the Packer does, so the tick encodes the borders and sectors it sends. -Dbench.sectorClients=250
 * of the clients watch sectors, each a random view of -Dbench.sectorSide=16 up to 64 blocks a side; the others watch
 * 2 x 2 blocks as cells. First it prints what the Packer's encodings of the 48 blocks cost. Run it on its own,
 * -Dtest=ClientUpdateBench#realBlocksTick, with -DargLine=-Xmx4g.
 */
@SpringBootTest(properties = {"cells.runmode=MANUAL", "cells.targetspeed=500", "spring.threads.virtual.enabled=true",
        "logging.level.slobben=WARN"})
class ClientUpdateBench {

    private static final int CLIENTS = Integer.getInteger("bench.clients", 2500);
    private static final int TICKS = Integer.getInteger("bench.ticks", 200);
    private static final int SECTOR_CLIENTS = Integer.getInteger("bench.sectorClients", 250);
    private static final int SECTOR_SIDE = Integer.getInteger("bench.sectorSide", 16);
    private static final int WARMUP_TICKS = 50;
    private static final int WORLD_BLOCKS = 40; // 40 x 40 blocks, every client watches 2 x 2 of them
    private static final int BORDER_BYTES = 800;
    private static final int REAL_WORLD_BLOCKS = 64;
    private static final int[] AGES = {1, 10, 30, 100, 300, 1000};
    private static final int SOUPS = 48;

    @Autowired
    private ClientService clientService;
    @Autowired
    private SimpleBrokerMessageHandler broker;
    @Autowired
    @Qualifier("brokerChannel")
    private AbstractSubscribableChannel brokerChannel;
    @Autowired
    @Qualifier("clientOutboundChannel")
    private SubscribableChannel clientOutboundChannel;
    @Autowired
    private Map<String, Block> blocks;

    @Test
    @Disabled("Benchmark")
    void clientUpdateTick() {
        Random random = new Random(1);
        for (int x = 0; x < WORLD_BLOCKS; x++) {
            for (int y = 0; y < WORLD_BLOCKS; y++) {
                Block block = new Block(x, y, UUID.randomUUID(), 8);
                byte[] cells = new byte[BORDER_BYTES];
                random.nextBytes(cells);
                EncodedBlock borders = new EncodedBlock(x, y, 1, EncodedBlockType.BORDER, cells);
                block.setEncodedBlockBorders(() -> borders);
                blocks.put(block.getKey(), block);
            }
        }
        AtomicInteger delivered = new AtomicInteger();
        clientOutboundChannel.subscribe(message -> {
            if (isMessage(message)) delivered.incrementAndGet();
        });
        for (int i = 0; i < CLIENTS; i++) {
            int x = random.nextInt(WORLD_BLOCKS - 1), y = random.nextInt(WORLD_BLOCKS - 1);
            watch(connect(i), x, x + 1, y, y + 1, false);
        }
        // the first update of a client sends full blocks, which these blocks don't have
        LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(1));
        delivered.set(0);

        List<Long> tickNanos = new ArrayList<>(), drainedNanos = new ArrayList<>();
        for (int tick = 0; tick < WARMUP_TICKS + TICKS; tick++) {
            int expected = delivered.get() + CLIENTS;
            long start = System.nanoTime();
            clientService.execute();
            long executed = System.nanoTime();
            long deadline = executed + TimeUnit.SECONDS.toNanos(10);
            while (delivered.get() < expected) {
                assertThat(System.nanoTime()).as("all messages delivered").isLessThan(deadline);
                LockSupport.parkNanos(50_000);
            }
            if (tick >= WARMUP_TICKS) {
                tickNanos.add(executed - start);
                drainedNanos.add(System.nanoTime() - start);
            }
        }
        assertThat(delivered.get()).isEqualTo((WARMUP_TICKS + TICKS) * CLIENTS);

        System.out.printf("%nRESULT %d clients, %d ticks, destination cache limit %d: tick %s, drained %s%n", CLIENTS, TICKS,
                ((DefaultSubscriptionRegistry) broker.getSubscriptionRegistry()).getCacheLimit(), stats(tickNanos, 1e6, "ms"),
                stats(drainedNanos, 1e6, "ms"));
    }

    @Test
    @Disabled("Benchmark")
    void realBlocksTick() {
        Random random = new Random(1);
        List<Block> soups = soups();
        for (int x = 0; x < REAL_WORLD_BLOCKS; x++) {
            for (int y = 0; y < REAL_WORLD_BLOCKS; y++) {
                Block cells = soups.get(random.nextInt(soups.size()));
                Block block = new Block(x, y, UUID.randomUUID(), 8);
                block.blockUpdated();
                block.setPackedCells(cells.getPackedCells());
                block.setSectorCounts(cells.getSectorCounts());
                blocks.put(block.getKey(), block);
            }
        }
        blocks.values().forEach(Packer::packAll);

        // every message the ClientService sends, and every one the sessions get, with its bytes
        AtomicLong sent = new AtomicLong(), delivered = new AtomicLong(), bytes = new AtomicLong(), sectorBytes = new AtomicLong();
        Set<String> sectorSessions = ConcurrentHashMap.newKeySet();
        brokerChannel.addInterceptor(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                if (isMessage(message)) sent.incrementAndGet();
                return message;
            }
        });
        clientOutboundChannel.subscribe(message -> {
            if (!isMessage(message)) return;
            int length = ((byte[]) message.getPayload()).length;
            bytes.addAndGet(length);
            if (sectorSessions.contains(SimpMessageHeaderAccessor.getSessionId(message.getHeaders()))) sectorBytes.addAndGet(length);
            delivered.incrementAndGet();
        });
        List<Boolean> watchesSectors = new ArrayList<>(Collections.nCopies(CLIENTS, false));
        Collections.fill(watchesSectors.subList(0, SECTOR_CLIENTS), true);
        Collections.shuffle(watchesSectors, random);
        for (int i = 0; i < CLIENTS; i++) {
            String sessionId = connect(i);
            boolean sectors = watchesSectors.get(i);
            int side = sectors ? SECTOR_SIDE + random.nextInt(REAL_WORLD_BLOCKS - SECTOR_SIDE + 1) : 2;
            int x = random.nextInt(REAL_WORLD_BLOCKS - side + 1), y = random.nextInt(REAL_WORLD_BLOCKS - side + 1);
            if (sectors) sectorSessions.add(sessionId);
            watch(sessionId, x, x + side - 1, y, y + side - 1, sectors);
        }
        awaitDelivery(sent, delivered);

        List<Long> tickNanos = new ArrayList<>(), drainedNanos = new ArrayList<>();
        long messages = 0, tickBytes = 0, tickSectorBytes = 0;
        for (int tick = 0; tick < WARMUP_TICKS + TICKS; tick++) {
            blocks.values().forEach(Packer::packAll);
            long sentBefore = sent.get(), bytesBefore = bytes.get(), sectorBytesBefore = sectorBytes.get();
            long start = System.nanoTime();
            clientService.execute();
            long executed = System.nanoTime();
            awaitDelivery(sent, delivered);
            if (tick >= WARMUP_TICKS) {
                tickNanos.add(executed - start);
                drainedNanos.add(System.nanoTime() - start);
                messages += sent.get() - sentBefore;
                tickBytes += bytes.get() - bytesBefore;
                tickSectorBytes += sectorBytes.get() - sectorBytesBefore;
            }
        }

        System.out.printf("%nRESULT %d clients of which %d watch sectors, views of %d to %d blocks a side, %d ticks: tick %s, drained %s%n",
                CLIENTS, SECTOR_CLIENTS, SECTOR_SIDE, REAL_WORLD_BLOCKS, TICKS, stats(tickNanos, 1e6, "ms"), stats(drainedNanos, 1e6, "ms"));
        System.out.printf("RESULT per tick %.0f messages of %.0f KB; a client that watches sectors gets %.1f KB/s%n",
                messages / (double) TICKS, tickBytes / 1024.0 / TICKS, tickSectorBytes / 1024.0 / Math.max(1, SECTOR_CLIENTS) / (TICKS * 0.5));
    }

    // Blocks of random cells, a third alive, after 1 to 1000 generations. Then it times the Packer's encodings of them,
    // as the first read after a tick encodes them.
    private static List<Block> soups() {
        Random random = new Random(2);
        AlgorithmGol algorithmGol = createAlgorithmGol(2000, 50);
        List<Block> soups = new ArrayList<>();
        for (int i = 0; i < SOUPS; i++) {
            Block block = new Block(0, 0, UUID.randomUUID(), 2000);
            for (int x = 1; x <= 2000; x++) {
                for (int y = 1; y <= 2000; y++) block.getCells()[x][y] = random.nextInt(3) == 0;
            }
            for (int generation = 0; generation < AGES[i % AGES.length]; generation++) nextState(algorithmGol, block);
            soups.add(block);
        }
        List<Long> sectorNanos = new ArrayList<>(), sectorBytes = new ArrayList<>(), borderNanos = new ArrayList<>(), borderBytes = new ArrayList<>();
        for (int round = 0; round < 300; round++) {
            for (Block block : soups) {
                Packer.packAll(block);
                long start = System.nanoTime();
                EncodedBlock sectors = block.getEncodedBlockSectors();
                long encoded = System.nanoTime();
                EncodedBlock borders = block.getEncodedBlockBorders();
                if (round >= 100) {
                    borderNanos.add(System.nanoTime() - encoded);
                    sectorNanos.add(encoded - start);
                    sectorBytes.add((long) sectors.encodedCells().length);
                    borderBytes.add((long) borders.encodedCells().length);
                }
            }
        }
        System.out.printf("%nRESULT encodings of %d blocks of random cells after %s generations: SECTOR %s, %s; BORDER %s, %s%n",
                SOUPS, Arrays.toString(AGES), stats(sectorNanos, 1e3, "us"), stats(sectorBytes, 1, "B"),
                stats(borderNanos, 1e3, "us"), stats(borderBytes, 1, "B"));
        return soups;
    }

    private String connect(int i) {
        String sessionId = Long.toHexString(i); // as Tomcat numbers its websocket sessions
        Message<byte[]> connect = stompMessage(SimpMessageType.CONNECT, sessionId);
        broker.handleMessage(connect);
        clientService.onConnect(new SessionConnectEvent(this, connect));
        broker.handleMessage(stompMessage(SimpMessageType.SUBSCRIBE, sessionId));
        return sessionId;
    }

    // the /client-update of a client with these blocks in view
    private void watch(String sessionId, int minX, int maxX, int minY, int maxY, boolean sectors) {
        clientService.updateClientBlocks(sessionId, new ClientUpdateRequest(minX, maxX, minY, maxY, sectors, null));
    }

    private static void awaitDelivery(AtomicLong sent, AtomicLong delivered) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (delivered.get() < sent.get()) {
            assertThat(System.nanoTime()).as("all messages delivered").isLessThan(deadline);
            LockSupport.parkNanos(50_000);
        }
    }

    private static boolean isMessage(Message<?> message) {
        return SimpMessageHeaderAccessor.getMessageType(message.getHeaders()) == SimpMessageType.MESSAGE;
    }

    private static Message<byte[]> stompMessage(SimpMessageType type, String sessionId) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(type);
        accessor.setSessionId(sessionId);
        accessor.setHeader(SimpMessageHeaderAccessor.HEART_BEAT_HEADER, new long[]{0, 0}); // no heartbeat checks
        if (type == SimpMessageType.SUBSCRIBE) {
            accessor.setSubscriptionId("sub-0");
            accessor.setDestination("/queue/blocks-user" + sessionId);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static String stats(List<Long> values, double unit, String name) {
        long[] sorted = values.stream().mapToLong(Long::longValue).sorted().toArray();
        return "avg %.2f %s, p50 %.2f, p90 %.2f, max %.2f".formatted(Arrays.stream(sorted).average().orElse(0) / unit, name,
                sorted[sorted.length / 2] / unit, sorted[(int) (sorted.length * 0.9)] / unit, sorted[sorted.length - 1] / unit);
    }
}
