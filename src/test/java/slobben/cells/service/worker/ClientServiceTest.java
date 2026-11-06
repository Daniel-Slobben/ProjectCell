package slobben.cells.service.worker;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.converter.ByteArrayMessageConverter;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.util.MimeTypeUtils;
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

import static org.assertj.core.api.Assertions.assertThat;
import static slobben.cells.service.worker.AlgorithmGolTests.nextState;

class ClientServiceTest {

    private final List<Message<?>> sent = new ArrayList<>();
    private final Map<String, Block> blocks = new HashMap<>();
    private final ClientService clientService;
    private final AlgorithmGol algorithmGol = AlgorithmGolLoadTest.algorithmGol(20, 5);

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
        Block active = block(0, 0);
        nextState(algorithmGol, active);
        Packer.packAll(active);
        Block fresh = block(-1, 0);
        Packer.packAll(fresh);
        blocks.put(active.getKey(), active);
        blocks.put(fresh.getKey(), fresh);
        blocks.put(block(5, 5).getKey(), block(5, 5)); // not packed yet: no encoding

        Client client = new Client();
        clientService.sendClientUpdate(client, Set.of(active.getKey(), fresh.getKey(), "5_5", "9_9"), true);

        assertThat(sent).hasSize(1);
        Message<?> message = sent.getFirst();
        assertThat(message.getHeaders().get("simpDestination")).isEqualTo("/topic/" + client.getClientId());
        assertThat(message.getHeaders().get("contentType").toString()).isEqualTo(MimeTypeUtils.APPLICATION_OCTET_STREAM_VALUE);
        List<EncodedBlock> received = BlockFrameReader.read((byte[]) message.getPayload());
        assertThat(received).containsExactlyInAnyOrder(active.getEncodedBlockBorders(), fresh.getEncodedBlockBorders());
        // a NEW block is sent in full, also as "border"
        assertThat(fresh.getEncodedBlockBorders().type()).isEqualTo(EncodedBlockType.FULL);
        assertThat(active.getEncodedBlockBorders().type()).isEqualTo(EncodedBlockType.BORDER);
    }

    @Test
    void sendsFullBlocksWhenAsked() {
        Block active = block(2, 3);
        nextState(algorithmGol, active);
        Packer.packAll(active);
        blocks.put(active.getKey(), active);

        clientService.sendClientUpdate(new Client(), Set.of(active.getKey()), false);

        assertThat(BlockFrameReader.read((byte[]) sent.getFirst().getPayload())).containsExactly(active.getEncodedBlock());
    }

    @Test
    void sendsEachFullBlockInItsOwnMessage() {
        Block first = block(2, 3);
        Block second = block(3, 3);
        for (Block block : List.of(first, second)) {
            nextState(algorithmGol, block);
            Packer.packAll(block);
            blocks.put(block.getKey(), block);
        }

        clientService.sendClientUpdate(new Client(), Set.of(first.getKey(), second.getKey()), false);

        assertThat(sent.stream().map(message -> BlockFrameReader.read((byte[]) message.getPayload())))
                .containsExactlyInAnyOrder(List.of(first.getEncodedBlock()), List.of(second.getEncodedBlock()));
    }

    // packed like Stitching does for NEW blocks
    private static Block block(int x, int y) {
        Block block = BlockUtils.setBlockToRandom(new Block(x, y, UUID.randomUUID(), 20), 3, 20);
        block.setPackedCells(PackedRows.pack(block.getCells()));
        return block;
    }
}
