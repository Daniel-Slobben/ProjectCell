package slobben.cells.service.worker;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.converter.ByteArrayMessageConverter;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import slobben.cells.dto.incoming.ClientUpdateRequest;
import slobben.cells.dto.outgoing.BlockFrameReader;
import slobben.cells.dto.outgoing.EncodedBlock;
import slobben.cells.dto.outgoing.EncodedBlockType;
import slobben.cells.entities.Block;
import slobben.cells.entities.Client;
import slobben.cells.service.ExecutorService;
import slobben.cells.service.workers.AlgorithmGol;
import slobben.cells.service.workers.ClientService;
import slobben.cells.service.workers.Packer;
import slobben.cells.util.BlockUtils;
import slobben.cells.util.PackedRows;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static slobben.cells.TestUtils.createAlgorithmGol;
import static slobben.cells.TestUtils.nextState;

class ClientServiceTest {

    private final List<Message<?>> sent = new CopyOnWriteArrayList<>(); // full blocks are sent from parallel threads
    private final Map<String, Block> blocks = new HashMap<>();
    private final ClientService clientService;
    private final AlgorithmGol algorithmGol = createAlgorithmGol(20, 5);

    ClientServiceTest() {
        MessageChannel channel = (message, timeout) -> sent.add(message);
        SimpMessagingTemplate template = new SimpMessagingTemplate(channel);
        // same converters as Spring's websocket broker configuration
        template.setMessageConverter(new CompositeMessageConverter(List.of(
                new StringMessageConverter(), new ByteArrayMessageConverter(), new JacksonJsonMessageConverter())));
        clientService = new ClientService(template, channel, new ExecutorService(), blocks);
        ReflectionTestUtils.setField(clientService, "sectorPeriodMs", 10000);
        ReflectionTestUtils.setField(clientService, "tileSize", 16);
        ReflectionTestUtils.setField(clientService, "maxCellBlocks", 2000);
    }

    @Test
    void sendsBlocksAsABinaryFrame() {
        // prepare
        Block active = getTestBlock(0, 0);
        nextState(algorithmGol, active);
        Packer.packAll(active);
        Block fresh = getTestBlock(-1, 0);
        Packer.packAll(fresh);
        blocks.put(active.getKey(), active);
        blocks.put(fresh.getKey(), fresh);
        blocks.put(getTestBlock(5, 5).getKey(), getTestBlock(5, 5)); // not packed yet: no encoding

        Client client = new Client("s1");

        // execute
        clientService.sendClientUpdate(client, Set.of(active.getKey(), fresh.getKey(), "5_5", "9_9"), true);

        // verify
        assertThat(sent).hasSize(1);
        Message<?> message = sent.getFirst();
        assertThat(message.getHeaders().get("simpDestination")).isEqualTo("/queue/blocks-users1");
        // the broker would take it as a heartbeat from the client
        assertThat(message.getHeaders().get("simpSessionId")).isNull();
        assertThat(message.getHeaders().get("contentType").toString()).isEqualTo(MimeTypeUtils.APPLICATION_OCTET_STREAM_VALUE);
        List<EncodedBlock> received = BlockFrameReader.read((byte[]) message.getPayload());
        assertThat(received).containsExactlyInAnyOrder(active.getEncodedBlockBorders(), fresh.getEncodedBlockBorders());
        // a NEW block is sent in full, also as "border"
        assertThat(fresh.getEncodedBlockBorders().type()).isEqualTo(EncodedBlockType.FULL);
        assertThat(active.getEncodedBlockBorders().type()).isEqualTo(EncodedBlockType.BORDER);
    }

    @Test
    void sendsFullBlocksWhenAsked() {
        // prepare
        Block active = getTestBlock(2, 3);
        nextState(algorithmGol, active);
        Packer.packAll(active);
        blocks.put(active.getKey(), active);

        // execute
        clientService.sendClientUpdate(new Client("s1"), Set.of(active.getKey()), false);

        // verify
        assertThat(BlockFrameReader.read((byte[]) sent.getFirst().getPayload())).containsExactly(active.getEncodedBlock());
    }

    @Test
    void sendsEachFullBlockInItsOwnMessage() {
        // prepare
        Block first = getTestBlock(2, 3);
        Block second = getTestBlock(3, 3);
        for (Block block : List.of(first, second)) {
            nextState(algorithmGol, block);
            Packer.packAll(block);
            blocks.put(block.getKey(), block);
        }

        // execute
        clientService.sendClientUpdate(new Client("s1"), Set.of(first.getKey(), second.getKey()), false);

        // verify
        assertThat(sent.stream().map(message -> BlockFrameReader.read((byte[]) message.getPayload())))
                .containsExactlyInAnyOrder(List.of(first.getEncodedBlock()), List.of(second.getEncodedBlock()));
    }

    @Test
    void sendsTheSectorsInViewEveryTenSecondsToSectorsOnly() {
        // prepare: ticks of 500 ms
        ReflectionTestUtils.setField(clientService, "targetSpeed", 500);
        Block block = getActiveTestBlock(2, 3);
        getActiveTestBlock(20, 20); // in another tile
        getTestBlock(3, 3); // NEW: no sectors yet
        connect("cells", view(2, 3, 3, 3, false));
        connect("sectors", view(2, 3, 3, 3, true));
        sent.clear();

        // execute
        for (int tick = 0; tick < 42; tick++) clientService.execute();

        // verify: the second client to connect gets its sectors on ticks 1, 21 and 41 and nothing else; cells their
        // borders, no sectors
        assertThat(sent.stream().filter(message -> destination(message).equals("/queue/blocks-usersectors")).map(ClientServiceTest::blocksOf))
                .containsExactly(List.of(block.getEncodedBlockSectors()), List.of(block.getEncodedBlockSectors()), List.of(block.getEncodedBlockSectors()));
        assertThat(sent.stream().filter(message -> destination(message).equals("/queue/blocks-usercells")).map(ClientServiceTest::blocksOf))
                .hasSize(42).noneMatch(frame -> frame.stream().anyMatch(encoded -> encoded.type() == EncodedBlockType.SECTOR));
    }

    @Test
    void spreadsTheSectorsOfClientsOverThePeriod() {
        // prepare: ticks of 500 ms, a period of 20 ticks
        ReflectionTestUtils.setField(clientService, "targetSpeed", 500);
        getActiveTestBlock(2, 3);
        for (int i = 0; i < 40; i++) connect("s" + i, view(2, 2, 3, 3, true));
        sent.clear();

        // execute
        List<Integer> perTick = new ArrayList<>();
        for (int tick = 0; tick < 20; tick++) {
            clientService.execute();
            perTick.add(sent.size());
            sent.clear();
        }

        // verify
        assertThat(perTick).containsOnly(2);
    }

    @Test
    void sendsTheFullBlocksNewInViewWhenPanning() {
        // prepare
        Block left = getActiveTestBlock(2, 3);
        Block right = getActiveTestBlock(3, 3);
        connect("s1", view(1, 2, 3, 3, false));
        List<List<EncodedBlock>> first = sent.stream().map(ClientServiceTest::blocksOf).toList();
        sent.clear();

        // execute
        clientService.updateClientBlocks("s1", view(2, 3, 3, 3, false));

        // verify
        assertThat(first).containsExactly(List.of(left.getEncodedBlock()));
        assertThat(sent.stream().map(ClientServiceTest::blocksOf)).containsExactly(List.of(right.getEncodedBlock()));
    }

    @Test
    void sendsTheSectorsNewInViewWhenPanningOverSectors() {
        // prepare
        Block left = getActiveTestBlock(2, 3);
        Block right = getActiveTestBlock(3, 3);
        connect("s1", view(1, 2, 3, 3, true));
        List<List<EncodedBlock>> first = sent.stream().map(ClientServiceTest::blocksOf).toList();
        sent.clear();

        // execute
        clientService.updateClientBlocks("s1", view(2, 3, 3, 3, true));

        // verify
        assertThat(first).containsExactly(List.of(left.getEncodedBlockSectors()));
        assertThat(sent.stream().map(ClientServiceTest::blocksOf)).containsExactly(List.of(right.getEncodedBlockSectors()));
    }

    @Test
    void sendsAllBlocksInViewWhenSwitchingBetweenCellsAndSectors() {
        // prepare
        Block first = getActiveTestBlock(2, 3);
        Block second = getActiveTestBlock(3, 3);
        connect("s1", view(2, 3, 3, 3, false));
        sent.clear();

        // execute
        clientService.updateClientBlocks("s1", view(2, 3, 3, 3, true));
        List<List<EncodedBlock>> toSectors = sent.stream().map(ClientServiceTest::blocksOf).toList();
        sent.clear();
        clientService.execute();
        clientService.execute();
        List<Message<?>> sectorTicks = List.copyOf(sent);
        sent.clear();
        clientService.updateClientBlocks("s1", view(2, 2, 3, 3, false));
        List<List<EncodedBlock>> toCells = sent.stream().map(ClientServiceTest::blocksOf).toList();

        // verify: watching sectors, no borders either; with ticks of 0 ms only tick 0 sends sectors
        assertThat(toSectors).hasSize(1);
        assertThat(toSectors.getFirst()).containsExactlyInAnyOrder(first.getEncodedBlockSectors(), second.getEncodedBlockSectors());
        assertThat(sectorTicks).hasSize(1);
        assertThat(toCells).containsExactly(List.of(first.getEncodedBlock()));
    }

    @Test
    void sendsTheSectorsNewInViewInMessagesOf250Blocks() {
        // prepare
        for (int x = 0; x < 251; x++) getActiveTestBlock(x, 0);

        // execute
        connect("s1", view(0, 250, 0, 0, true));

        // verify
        assertThat(sent.stream().map(message -> blocksOf(message).size())).containsExactly(250, 1);
    }

    @Test
    void sendsTheSectorsPerTileWithTheSameBytesForEveryClient() {
        // prepare: tile 0/0 has 0/0 and 15/15, tile 1/0 has 16/0; the view only touches 15/15 and 16/0
        Block first = getActiveTestBlock(0, 0);
        Block second = getActiveTestBlock(15, 15);
        Block third = getActiveTestBlock(16, 0);
        connect("s1", view(15, 16, 0, 15, true));
        connect("s2", view(15, 16, 0, 15, true));
        sent.clear();

        // execute: s1 on tick 0, s2 on tick 1
        clientService.execute();
        clientService.execute();

        // verify
        List<Message<?>> s1 = sent.stream().filter(message -> destination(message).equals("/queue/blocks-users1")).toList();
        List<Message<?>> s2 = sent.stream().filter(message -> destination(message).equals("/queue/blocks-users2")).toList();
        assertThat(s1.stream().map(ClientServiceTest::blocksOf)).containsExactlyInAnyOrder(
                List.of(first.getEncodedBlockSectors(), second.getEncodedBlockSectors()), List.of(third.getEncodedBlockSectors()));
        assertThat(s1.stream().map(Message::getPayload).toList()).hasSize(2)
                .allMatch(payload -> s2.stream().anyMatch(message -> message.getPayload() == payload));
    }

    @Test
    void sendsTheFullBlocksAskedForAgainInView() {
        // prepare
        Block block = getActiveTestBlock(2, 3);
        getActiveTestBlock(3, 3);
        connect("s1", view(2, 3, 3, 3, false));
        sent.clear();

        // execute
        clientService.updateClientBlocks("s1", new ClientUpdateRequest(2, 3, 3, 3, false, new String[]{"2/3", "9/9"}));

        // verify
        assertThat(sent.stream().map(ClientServiceTest::blocksOf)).containsExactly(List.of(block.getEncodedBlock()));
    }

    @Test
    void ignoresAViewOfMoreThan2000BlocksAsCells() {
        // prepare
        getActiveTestBlock(2, 3);
        clientService.onConnect(new SessionConnectEvent(this, stompMessage("s1")));

        // execute
        clientService.updateClientBlocks("s1", view(0, 50, 0, 50, false));

        // verify
        assertThat(sent).isEmpty();
    }

    @Test
    void ignoresViewsThatAreTooBig() {
        // prepare
        getActiveTestBlock(2, 3);
        clientService.onConnect(new SessionConnectEvent(this, stompMessage("s1")));

        // execute: an int subtraction would wrap these to small counts
        clientService.updateClientBlocks("s1", view(Integer.MIN_VALUE, Integer.MAX_VALUE, 0, 0, false));
        clientService.updateClientBlocks("s1", view(Integer.MIN_VALUE, Integer.MAX_VALUE, 0, 0, true));

        // verify
        assertThat(sent).isEmpty();
    }

    @Test
    void rejectsUpsideDownViews() {
        assertThatThrownBy(() -> view(3, 2, 3, 3, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> view(3, 3, 3, 2, true)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void endsAViewAtTheEdgeOfTheInts() {
        // prepare
        getActiveTestBlock(Integer.MAX_VALUE, 0);

        // execute
        connect("s1", view(Integer.MAX_VALUE - 1, Integer.MAX_VALUE, 0, 0, false));

        // verify
        assertThat(sent).hasSize(1);
    }

    @Test
    void readsAnUpdateWithoutSectorsOrResend() {
        // prepare
        Message<byte[]> message = MessageBuilder.withPayload("{\"minX\":1,\"maxX\":2,\"minY\":3,\"maxY\":4}".getBytes())
                .setHeader("contentType", MimeTypeUtils.APPLICATION_JSON).build();

        // execute
        ClientUpdateRequest request = (ClientUpdateRequest) new JacksonJsonMessageConverter().fromMessage(message, ClientUpdateRequest.class);

        // verify
        assertThat(request.blockCount()).isEqualTo(4);
        assertThat(request.watchesSectors()).isFalse();
        assertThat(request.resendKeys()).isEmpty();
    }

    @Test
    void sendsNoBlocksWhenTheFirstUpdateFindsNothing() {
        // prepare
        getActiveTestBlock(2, 3);

        // execute
        connect("s1", view(7, 7, 7, 8, false));

        // verify
        assertThat(sent).hasSize(1);
        assertThat(new String((byte[]) sent.getFirst().getPayload())).isEqualTo("{\"type\":\"NO_BLOCKS\"}");
    }

    @Test
    void startsOverAfterNoBlocks() {
        // prepare: nothing at 2/3 yet
        connect("s1", view(1, 2, 3, 3, false));
        Block block = getActiveTestBlock(2, 3);
        sent.clear();

        // execute: a view that overlaps the first one
        clientService.updateClientBlocks("s1", view(2, 3, 3, 3, false));

        // verify: 2/3 counts as new in view
        assertThat(sent.stream().map(ClientServiceTest::blocksOf)).containsExactly(List.of(block.getEncodedBlock()));
    }

    @Test
    void sendsNoBlocksOnlyForTheFirstUpdate() {
        // prepare
        getActiveTestBlock(2, 3);
        connect("s1", view(2, 2, 3, 3, false));
        sent.clear();

        // execute: panning to where there are no blocks
        clientService.updateClientBlocks("s1", view(7, 7, 7, 7, false));

        // verify
        assertThat(sent).isEmpty();
    }

    @Test
    void sendsBordersOnTheFirstTickAfterTheFullBlocks() {
        // prepare
        Block block = getActiveTestBlock(2, 3);
        connect("s1", view(2, 2, 3, 3, false));
        sent.clear();

        // execute
        clientService.execute();

        // verify: the client-update already sent the full block
        assertThat(sent).hasSize(1);
        assertThat(BlockFrameReader.read((byte[]) sent.getFirst().getPayload())).containsExactly(block.getEncodedBlockBorders());
    }

    @Test
    void forgetsAClientWhenItsSessionCloses() {
        // prepare
        getActiveTestBlock(2, 3);
        connect("s1", view(2, 2, 3, 3, false));
        sent.clear();

        // execute
        clientService.onDisconnect(new SessionDisconnectEvent(this, stompMessage("s1"), "s1", CloseStatus.NORMAL));
        clientService.execute();
        clientService.updateClientBlocks("s1", view(2, 2, 3, 3, false));

        // verify
        assertThat(sent).isEmpty();
    }

    @Test
    void closesASessionThatAsksForMoreBlocksThanItsBudget() {
        // prepare: a budget of 20 blocks that doesn't refill, each view costs 1 + 9
        ReflectionTestUtils.setField(clientService, "maxCellBlocks", 10);
        getActiveTestBlock(0, 0);
        getActiveTestBlock(20, 0);
        connect("s1", view(0, 2, 0, 2, false));
        clientService.updateClientBlocks("s1", view(10, 12, 0, 2, false));
        sent.clear();

        // execute
        clientService.updateClientBlocks("s1", view(20, 22, 0, 2, false));

        // verify: no blocks, only the ERROR that closes the session
        assertThat(sent).singleElement().satisfies(message -> {
            StompHeaderAccessor error = StompHeaderAccessor.wrap(message);
            assertThat(error.getCommand()).isEqualTo(StompCommand.ERROR);
            assertThat(error.getSessionId()).isEqualTo("s1");
        });
    }

    @Test
    void chargesUpdatesThatSendNothing() {
        // prepare: a budget of 20 that doesn't refill, an empty world
        ReflectionTestUtils.setField(clientService, "maxCellBlocks", 10);
        clientService.onConnect(new SessionConnectEvent(this, stompMessage("s1")));
        for (int i = 0; i < 20; i++) clientService.updateClientBlocks("s1", view(0, 0, 0, 0, false));
        assertThat(closed("s1")).isFalse();

        // execute
        clientService.updateClientBlocks("s1", view(0, 0, 0, 0, false));

        // verify
        assertThat(closed("s1")).isTrue();
    }

    @Test
    void chargesSectorsAsAHundredthOfABlock() {
        // prepare: as full blocks these 1900 would be far over the budget of 20; as sectors they cost 1 + 19
        ReflectionTestUtils.setField(clientService, "maxCellBlocks", 10);
        for (int x = 0; x < 1900; x++) blocks.put(BlockUtils.getKey(x, 0), getTestBlock(x, 0));
        connect("s1", view(0, 1899, 0, 0, true));
        assertThat(closed("s1")).isFalse();

        // execute: the budget is spent, so the next update is one too many
        clientService.updateClientBlocks("s1", view(0, 1899, 0, 0, true));

        // verify
        assertThat(closed("s1")).isTrue();
    }

    @Test
    void chargesResentBlocks() {
        // prepare: a budget of 20, of which the first view costs 1 + 9 and resending its 9 blocks another 1 + 9
        ReflectionTestUtils.setField(clientService, "maxCellBlocks", 10);
        getActiveTestBlock(0, 0);
        connect("s1", view(0, 2, 0, 2, false));
        String[] inView = {"0/0", "0/1", "0/2", "1/0", "1/1", "1/2", "2/0", "2/1", "2/2"};
        clientService.updateClientBlocks("s1", new ClientUpdateRequest(0, 2, 0, 2, false, inView));
        assertThat(closed("s1")).isFalse();

        // execute
        clientService.updateClientBlocks("s1", view(0, 2, 0, 2, false));

        // verify
        assertThat(closed("s1")).isTrue();
    }

    private boolean closed(String sessionId) {
        return sent.stream().map(StompHeaderAccessor::wrap)
                .anyMatch(accessor -> accessor.getCommand() == StompCommand.ERROR && sessionId.equals(accessor.getSessionId()));
    }

    private void connect(String sessionId, ClientUpdateRequest view) {
        clientService.onConnect(new SessionConnectEvent(this, stompMessage(sessionId)));
        clientService.updateClientBlocks(sessionId, view);
    }

    private static ClientUpdateRequest view(int minX, int maxX, int minY, int maxY, boolean sectors) {
        return new ClientUpdateRequest(minX, maxX, minY, maxY, sectors, null);
    }

    private static List<EncodedBlock> blocksOf(Message<?> message) {
        return BlockFrameReader.read((byte[]) message.getPayload());
    }

    private static String destination(Message<?> message) {
        return (String) message.getHeaders().get("simpDestination");
    }

    private static Message<byte[]> stompMessage(String sessionId) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.CONNECT);
        accessor.setSessionId(sessionId);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Block getActiveTestBlock(int x, int y) {
        Block block = getTestBlock(x, y);
        nextState(algorithmGol, block);
        Packer.packAll(block);
        blocks.put(block.getKey(), block);
        return block;
    }

    // packed like Stitching does for NEW blocks
    private static Block getTestBlock(int x, int y) {
        Block block = BlockUtils.setBlockToRandom(new Block(x, y, UUID.randomUUID(), 20), 3, 20);
        block.setPackedCells(PackedRows.pack(block.getCells()));
        return block;
    }
}
