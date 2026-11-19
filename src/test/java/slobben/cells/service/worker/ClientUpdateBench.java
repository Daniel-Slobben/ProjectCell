package slobben.cells.service.worker;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.Message;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.DefaultSubscriptionRegistry;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import slobben.cells.dto.incoming.ClientUpdateRequest;
import slobben.cells.dto.outgoing.EncodedBlock;
import slobben.cells.dto.outgoing.EncodedBlockType;
import slobben.cells.entities.Block;
import slobben.cells.service.workers.ClientService;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Times ClientService's tick with many STOMP sessions on the real broker, without websockets: each client is the
 * CONNECT and SUBSCRIBE a browser sends, and the outbound channel only counts the messages the sessions would get.
 * "tick" is ClientService.execute, "drained" is until the outbound channel has handed over every message.
 * <p>
 * mvn test -Dtest=ClientUpdateBench -Djunit.jupiter.conditions.deactivate='org.junit.*DisabledCondition',
 * with -Dbench.clients=2500 -Dbench.ticks=200
 * <p>
 * sectorTick: the same in a world of 64 x 64 blocks that each have sectors, ticks of 500 ms, where
 * -Dbench.sectorClients=500 of the clients watch sectors zoomed all the way out, the whole world in view: each gets the
 * sectors of all blocks once every 20 ticks, a 20th of them per tick, and every 20th tick writes the tiles first. Run
 * it on its own: -Dtest=ClientUpdateBench#sectorTick
 */
@SpringBootTest(properties = {"cells.runmode=MANUAL", "spring.threads.virtual.enabled=true", "logging.level.slobben=WARN"})
class ClientUpdateBench {

    private static final int CLIENTS = Integer.getInteger("bench.clients", 2500);
    private static final int TICKS = Integer.getInteger("bench.ticks", 200);
    private static final int SECTOR_CLIENTS = Integer.getInteger("bench.sectorClients", 500);
    private static final int WARMUP_TICKS = 50;
    private static final int WORLD_BLOCKS = 40; // 40 x 40 blocks, every client watches 2 x 2 of them
    private static final int BORDER_BYTES = 800;
    private static final int MAP_WORLD_BLOCKS = 64;
    private static final int SECTOR_BYTES = 124; // the mean zstd SECTOR frame of SectorFrameSizeTest

    @Autowired
    private ClientService clientService;
    @Autowired
    private SimpleBrokerMessageHandler broker;
    @Autowired
    @Qualifier("clientOutboundChannel")
    private SubscribableChannel clientOutboundChannel;
    @Autowired
    private Map<String, Block> blocks;

    @Test
    @Disabled("Benchmark")
    void clientUpdateTick() {
        Random random = new Random(1);
        AtomicInteger delivered = setUp(random, WORLD_BLOCKS, 0);

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
                ((DefaultSubscriptionRegistry) broker.getSubscriptionRegistry()).getCacheLimit(), stats(tickNanos), stats(drainedNanos));
    }

    @Test
    @Disabled("Benchmark")
    void sectorTick() {
        ReflectionTestUtils.setField(clientService, "targetSpeed", 500);
        int sectorTicks = 20, sectorMessages = (MAP_WORLD_BLOCKS / 16) * (MAP_WORLD_BLOCKS / 16); // a message per tile of 16 x 16
        AtomicInteger delivered = setUp(new Random(1), MAP_WORLD_BLOCKS, SECTOR_BYTES);
        for (int i = 0; i < SECTOR_CLIENTS; i++) {
            clientService.updateClientBlocks(Long.toHexString(i), new ClientUpdateRequest(0, MAP_WORLD_BLOCKS - 1, 0, MAP_WORLD_BLOCKS - 1, true, null));
        }
        LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(1));
        delivered.set(0);

        List<Long> tickNanos = new ArrayList<>(), drainedNanos = new ArrayList<>();
        List<Long> sectorTickNanos = new ArrayList<>(), sectorDrainedNanos = new ArrayList<>();
        for (int tick = 1; tick <= (WARMUP_TICKS + TICKS) / sectorTicks * sectorTicks; tick++) {
            // client i connected i-th, so it gets its sectors on the ticks where tick % 20 == i % 20; the first one
            // due, on tick 1, has the tiles written
            boolean sectorTick = tick % sectorTicks == 1;
            int phase = tick % sectorTicks, sectorClients = (SECTOR_CLIENTS + sectorTicks - 1 - phase) / sectorTicks;
            int expected = delivered.get() + CLIENTS - SECTOR_CLIENTS + sectorClients * sectorMessages;
            long start = System.nanoTime();
            clientService.execute();
            long executed = System.nanoTime();
            long deadline = executed + TimeUnit.SECONDS.toNanos(30);
            while (delivered.get() < expected) {
                assertThat(System.nanoTime()).as("all messages delivered").isLessThan(deadline);
                LockSupport.parkNanos(50_000);
            }
            if (tick > WARMUP_TICKS) {
                (sectorTick ? sectorTickNanos : tickNanos).add(executed - start);
                (sectorTick ? sectorDrainedNanos : drainedNanos).add(System.nanoTime() - start);
            }
        }

        System.out.printf("%nRESULT %d clients of which %d watch sectors, %d blocks, %d sector messages each: tick %s, drained %s%n",
                CLIENTS, SECTOR_CLIENTS, MAP_WORLD_BLOCKS * MAP_WORLD_BLOCKS, sectorMessages, stats(tickNanos), stats(drainedNanos));
        System.out.printf("RESULT ticks that write the tiles (n=%d): tick %s, drained %s%n", sectorTickNanos.size(), stats(sectorTickNanos), stats(sectorDrainedNanos));
    }

    // A world of side x side blocks with borders, and sectors if sectorBytes > 0, and the clients, each watching 2 x 2
    // blocks. Returns the count of messages delivered to the sessions.
    private AtomicInteger setUp(Random random, int side, int sectorBytes) {
        for (int x = 0; x < side; x++) {
            for (int y = 0; y < side; y++) {
                Block block = new Block(x, y, UUID.randomUUID(), 8);
                byte[] cells = new byte[BORDER_BYTES];
                random.nextBytes(cells);
                EncodedBlock borders = new EncodedBlock(x, y, 1, EncodedBlockType.BORDER, cells);
                block.setEncodedBlockBorders(() -> borders);
                if (sectorBytes > 0) {
                    byte[] sectorCells = new byte[sectorBytes];
                    random.nextBytes(sectorCells);
                    EncodedBlock sectors = new EncodedBlock(x, y, 1, EncodedBlockType.SECTOR, sectorCells);
                    block.setEncodedBlockSectors(() -> sectors);
                }
                blocks.put(block.getKey(), block);
            }
        }
        AtomicInteger delivered = new AtomicInteger();
        clientOutboundChannel.subscribe(message -> {
            if (SimpMessageHeaderAccessor.getMessageType(message.getHeaders()) == SimpMessageType.MESSAGE) {
                delivered.incrementAndGet();
            }
        });
        clientService.execute(); // builds the sector map, before any client
        for (int i = 0; i < CLIENTS; i++) {
            String sessionId = Long.toHexString(i); // as Tomcat numbers its websocket sessions
            Message<byte[]> connect = stompMessage(SimpMessageType.CONNECT, sessionId);
            broker.handleMessage(connect);
            clientService.onConnect(new SessionConnectEvent(this, connect));
            broker.handleMessage(stompMessage(SimpMessageType.SUBSCRIBE, sessionId));
            int x = random.nextInt(side - 1), y = random.nextInt(side - 1);
            clientService.updateClientBlocks(sessionId, new ClientUpdateRequest(x, x + 1, y, y + 1, false, null));
        }
        // the first update of a client sends full blocks, which these blocks don't have
        LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(1));
        delivered.set(0);
        return delivered;
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

    private static String stats(List<Long> nanos) {
        long[] sorted = nanos.stream().mapToLong(Long::longValue).sorted().toArray();
        return "avg %.2f ms, p50 %.2f ms, p90 %.2f ms".formatted(Arrays.stream(sorted).average().orElse(0) / 1e6,
                sorted[sorted.length / 2] / 1e6, sorted[(int) (sorted.length * 0.9)] / 1e6);
    }
}
