package slobben.cells.util;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import slobben.cells.entities.Pattern;

import java.io.File;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@ComponentScan("slobben.cells.service")
@ActiveProfiles(profiles = "normal")
class RleReaderTest {

    @Autowired
    private RleReader rleReader;

    public static Stream<String> allPatterns() {
        File file = new File("src/main/resources/patterns");
        return Arrays.stream(Objects.requireNonNull(file.listFiles()))
                .map(File::getPath)
                .map(path -> path.replace("src/main/resources/patterns/", ""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"foureatershasslingfourbookends_synth", "fireshiprake", "pentapole", "1beacon_synth", "2c5greyshipwflatfrontandwick"})
    @SneakyThrows
    void readPatternFromFilename(String filename) {
        Pattern pattern = rleReader.readPatternFromFilename(filename);

        assertThat(pattern).isNotNull();
    }

    @Disabled("Few errors on too high dimensions.")
    @ParameterizedTest
    @MethodSource("allPatterns")
    @SneakyThrows
    void readRandomPattern(String fileName) {
        Pattern pattern = rleReader.readPatternFromFilename(fileName);
        assertThat(pattern).isNotNull();
    }
}
