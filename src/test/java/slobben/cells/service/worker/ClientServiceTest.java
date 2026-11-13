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
import static slobben.cells.service.worker.AlgorithmGolTest.nextState;

class ClientServiceTest {

    private final List<Message<?>> sent = new CopyOnWriteArrayList<>(); // full blocks are sent from parallel threads
    private final Map<String, Block> blocks = new HashMap<>();
    private final ClientService clientService;
    private final AlgorithmGol algorithmGol = AlgorithmGolLoadTest.createAlgorithmGol(20, 5);

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
    void sendsNoBlocksWhenTheFirstUpdateFindsNothing() {
        // prepare
        clientService.onConnect(new SessionConnectEvent(this, stompMessage("s1")));

        // execute
        clientService.updateClientBlocks("s1", new ClientUpdateRequest(new String[0], new String[]{"7/7", "7/8"}));

        // verify
        assertThat(sent).hasSize(1);
        assertThat(new String((byte[]) sent.getFirst().getPayload())).isEqualTo("{\"type\":\"NO_BLOCKS\"}");
    }

    @Test
    void sendsNoBlocksOnlyForTheFirstUpdate() {
        // prepare
        Block block = getTestBlock(2, 3);
        nextState(algorithmGol, block);
        Packer.packAll(block);
        blocks.put(block.getKey(), block);
        clientService.onConnect(new SessionConnectEvent(this, stompMessage("s1")));
        clientService.updateClientBlocks("s1", new ClientUpdateRequest(new String[0], new String[]{block.getKey()}));
        sent.clear();

        // execute: panning to where there are no blocks
        clientService.updateClientBlocks("s1", new ClientUpdateRequest(new String[]{block.getKey()}, new String[]{"7/7"}));

        // verify
        assertThat(sent).isEmpty();
    }

    @Test
    void forgetsAClientWhenItsSessionCloses() {
        // prepare
        Block block = getTestBlock(2, 3);
        nextState(algorithmGol, block);
        Packer.packAll(block);
        blocks.put(block.getKey(), block);
        clientService.onConnect(new SessionConnectEvent(this, stompMessage("s1")));
        clientService.updateClientBlocks("s1", new ClientUpdateRequest(new String[0], new String[]{block.getKey()}));
        sent.clear();

        // execute
        clientService.onDisconnect(new SessionDisconnectEvent(this, stompMessage("s1"), "s1", CloseStatus.NORMAL));
        clientService.execute();
        clientService.updateClientBlocks("s1", new ClientUpdateRequest(new String[0], new String[]{block.getKey()}));

        // verify
        assertThat(sent).isEmpty();
    }

    private static Message<byte[]> stompMessage(String sessionId) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.CONNECT);
        accessor.setSessionId(sessionId);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    // packed like Stitching does for NEW blocks
    private static Block getTestBlock(int x, int y) {
        Block block = BlockUtils.setBlockToRandom(new Block(x, y, UUID.randomUUID(), 20), 3, 20);
        block.setPackedCells(PackedRows.pack(block.getCells()));
        return block;
    }
}
