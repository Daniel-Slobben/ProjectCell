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
        clientService = new ClientService(template, new ExecutorService(), blocks);
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
        getActiveTestBlock(5, 5); // out of view
        getTestBlock(3, 3); // NEW: no sectors yet
        connect("cells", view(2, 3, 3, 3, false));
        connect("sectors", view(2, 3, 3, 3, true));
        sent.clear();

        // execute
        for (int tick = 0; tick < 41; tick++) clientService.execute();

        // verify: sectors on ticks 0, 20 and 40 and nothing else; cells their borders, no sectors
        assertThat(sent.stream().filter(message -> destination(message).equals("/queue/blocks-usersectors")).map(ClientServiceTest::blocksOf))
                .containsExactly(List.of(block.getEncodedBlockSectors()), List.of(block.getEncodedBlockSectors()), List.of(block.getEncodedBlockSectors()));
        assertThat(sent.stream().filter(message -> destination(message).equals("/queue/blocks-usercells")).map(ClientServiceTest::blocksOf))
                .hasSize(41).noneMatch(frame -> frame.stream().anyMatch(encoded -> encoded.type() == EncodedBlockType.SECTOR));
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
    void sendsTheSectorsInMessagesOf250Blocks() {
        // prepare
        for (int x = 0; x < 251; x++) getActiveTestBlock(x, 0);
        connect("s1", view(0, 250, 0, 0, true));
        sent.clear();

        // execute
        clientService.execute();

        // verify
        assertThat(sent.stream().map(message -> blocksOf(message).size())).containsExactly(250, 1);
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
