package slobben.cells.dto.outgoing;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Settings still sends its blocks as JSON, in the same shape as before the binary frames:
 * {x, y, generation, encodedCells: Base64 of the LZ4 bytes, type: "FULL" / "BORDER"}.
 */
class EncodedBlockJsonTest {

    @Test
    void settingsKeepsTheBase64JsonShape() {
        byte[] payload = {(byte) 0xF0, 0x00, 0x7F, (byte) 0xFF, 0x10};
        Settings settings = new Settings(2000, UUID.randomUUID(), null,
                List.of(new EncodedBlock(-3, 4, 17, EncodedBlockType.FULL, payload)));

        JsonMapper mapper = JsonMapper.builder().build();
        JsonNode block = mapper.readTree(mapper.writeValueAsString(settings)).get("blocks").get(0);

        assertThat(block.propertyNames()).containsExactlyInAnyOrder("x", "y", "generation", "encodedCells", "type");
        assertThat(block.get("x").asInt()).isEqualTo(-3);
        assertThat(block.get("y").asInt()).isEqualTo(4);
        assertThat(block.get("generation").asInt()).isEqualTo(17);
        assertThat(block.get("type").asString()).isEqualTo("FULL");
        assertThat(block.get("encodedCells").asString()).isEqualTo(Base64.getEncoder().encodeToString(payload));
    }
}
