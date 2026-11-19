package slobben.cells.service.worker;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import slobben.cells.dto.incoming.ClientUpdateRequest;
import slobben.cells.entities.Block;
import slobben.cells.service.ExecutorService;
import slobben.cells.service.workers.ClientService;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Times what the server does with a /client-update: the JSON to a ClientUpdateRequest, as Spring converts it, then
 * ClientService.updateClientBlocks. A client pans one block at a time back and forth over a world of 64 x 64 blocks,
 * with a view of side x side blocks. The blocks have no encodings, so nothing is sent: only the bookkeeping counts.
 * <p>
 * mvn test -Dtest=ClientUpdateRequestBench -Djunit.jupiter.conditions.deactivate='org.junit.*DisabledCondition'
 */
class ClientUpdateRequestBench {

    private static final int WORLD = 64;
    private static final int UPDATES = Integer.getInteger("bench.updates", 20000);
    private static final int ROUNDS = 5;

    private final JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
    private final Map<String, Block> blocks = new HashMap<>();
    private final MessageChannel channel = (message, timeout) -> true;
    private final ClientService clientService = new ClientService(new SimpMessagingTemplate(channel), channel, new ExecutorService(), blocks);
    private int sessions = 0;

    {
        ReflectionTestUtils.setField(clientService, "sectorPeriodMs", 10000);
        ReflectionTestUtils.setField(clientService, "tileSize", 16);
        ReflectionTestUtils.setField(clientService, "maxCellBlocks", 2000);
        ReflectionTestUtils.setField(clientService, "blocksPerSecond", Integer.MAX_VALUE);
    }

    @Test
    @Disabled("Benchmark")
    void panning() {
        for (int x = 0; x < WORLD; x++) {
            for (int y = 0; y < WORLD; y++) blocks.put(x + "/" + y, new Block(x, y, UUID.randomUUID(), 8));
        }
        for (int round = 0; round <= ROUNDS; round++) {
            // round 0 warms up
            for (int[] view : new int[][]{{3, 0}, {16, 0}, {22, 1}, {40, 1}}) {
                double nanos = time(view[0], view[1] == 1);
                if (round > 0) System.out.printf("RESULT %-8s %2dx%-2d %8.0f ns/update%n", view[1] == 1 ? "sectors" : "cells", view[0], view[0], nanos);
            }
        }
    }

    private double time(int side, boolean sectors) {
        String sessionId = "s" + sessions++;
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.CONNECT);
        accessor.setSessionId(sessionId);
        clientService.onConnect(new SessionConnectEvent(this, MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders())));
        clientService.updateClientBlocks(sessionId, read(payload(-1, 0, side, sectors)));

        // back and forth: 0 -> 1 -> ... -> last -> ... -> 0
        int last = WORLD - side;
        List<byte[]> sweep = new ArrayList<>();
        for (int i = 0; i < 2 * last; i++) {
            int from = i < last ? i : 2 * last - i, to = i < last ? i + 1 : 2 * last - i - 1;
            sweep.add(payload(from, to, side, sectors));
        }
        long start = System.nanoTime();
        for (int i = 0; i < UPDATES; i++) clientService.updateClientBlocks(sessionId, read(sweep.get(i % sweep.size())));
        return (System.nanoTime() - start) / (double) UPDATES;
    }

    private ClientUpdateRequest read(byte[] json) {
        return (ClientUpdateRequest) converter.fromMessage(MessageBuilder.withPayload(json)
                .setHeader("contentType", MimeTypeUtils.APPLICATION_JSON).build(), ClientUpdateRequest.class);
    }

    // the view of side x side blocks from x = to, y = 0; from is the x before, -1 for the first update
    private static byte[] payload(int from, int to, int side, boolean sectors) {
        return "{\"minX\":%d,\"maxX\":%d,\"minY\":0,\"maxY\":%d,\"sectors\":%b,\"resend\":[]}"
                .formatted(to, to + side - 1, side - 1, sectors).getBytes(StandardCharsets.UTF_8);
    }
}
