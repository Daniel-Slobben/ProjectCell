package slobben.cells.service.worker;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.converter.ByteArrayMessageConverter;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;
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

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

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
        ReflectionTestUtils.setField(clientService, "blockSize", 20);
    }

    @Test
    void sendsBlocksAsABinaryFrame() {
        Block active = block(0, 0);
        algorithmGol.setNextState(active);
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
        algorithmGol.setNextState(active);
        Packer.packAll(active);
        blocks.put(active.getKey(), active);

        clientService.sendClientUpdate(new Client(), Set.of(active.getKey()), false);

        assertThat(BlockFrameReader.read((byte[]) sent.getFirst().getPayload())).containsExactly(active.getEncodedBlock());
    }

    private static Block block(int x, int y) {
        return BlockUtils.setBlockToRandom(new Block(x, y, UUID.randomUUID(), 20), 3, 20);
    }
}
