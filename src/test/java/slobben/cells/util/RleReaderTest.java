package slobben.cells.util;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import slobben.cells.entities.Pattern;

import java.io.File;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class RleReaderTest {

    private final RleReader rleReader = new RleReader();

    public static Stream<String> allPatterns() {
        File file = new File("src/main/resources/patterns");
        return Arrays.stream(Objects.requireNonNull(file.listFiles()))
                .flatMap(cat -> Arrays.stream(Objects.requireNonNull(cat.listFiles())))
                .map(File::getPath)
                .map(path -> path.replace("src/main/resources/patterns/", ""));
    }

    @ParameterizedTest
    @Disabled
    @ValueSource(strings = {"syntheses/foureatershasslingfourbookends_synth", "gardens-of-eden/gardenofeden1", "oscillators/pentapole"})
    @SneakyThrows
    void readPatternFromFilename(String filename) {
        Pattern pattern = rleReader.readPatternFromFilename(filename);

        assertThat(pattern).isNotNull();
    }

    @Test
    @Disabled
    @SneakyThrows
    void randomPatternFromCategory() {
        Pattern pattern = rleReader.readRandomPatternFromCategory("guns");

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
