package slobben.cells.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import slobben.cells.dto.internal.Coordinates;
import slobben.cells.dto.internal.Pattern;

import java.io.*;
import java.util.Random;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RleReader {

    private final WorldEditor worldEditor;

    private static final int DIMENSION_LIMIT = 50_000;
    private static final String DIR = "patterns/";
    private static final Random random = new Random();
    private static final ResourcePatternResolver RESOLVER = new PathMatchingResourcePatternResolver(RleReader.class.getClassLoader());

    public void writePatternToWorld(String name, Coordinates coordinates, UUID hitId, byte rotation) throws IOException {
        name = name.replace(".rle", "");
        Resource resource = RESOLVER.getResource("classpath:" + DIR + name + ".rle");
        if (!resource.exists()) {
            throw new FileNotFoundException("No pattern on classpath: " + name + ".rle");
        }
        try (InputStream in = resource.getInputStream()) {
            writeRleInputStreamToWorld(name, hitId, coordinates, rotation, in);
        }
    }


    private void writeRleInputStreamToWorld(String name, UUID hitId, Coordinates worldStartingCoordinates, byte rotation, InputStream inputStream) throws IOException {
        if (rotation < 0 || rotation > 3) {
            throw new IllegalArgumentException("Rotation must be 0, 1, 2 or 3 but was %s".formatted(rotation));
        }
        log.info("Reading file {}", name);
        int x = 0;
        int y = 0;

        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

        int xIndex = 0;
        int yIndex = 0;

        StringBuilder numberBuffer = new StringBuilder();
        while (bufferedReader.ready()) {
            String line = bufferedReader.readLine();

            if (line.trim().startsWith("#")) continue;
            if (line.isBlank()) continue;
            if (line.contains("rule")) {
                int commaCounter = 0;
                for (char c : line.toCharArray()) {
                    if (c == 'y') {
                        // reversed the x and y from a standard rle pattern
                        // because I didn't consult what the standard was when starting.
                        y = Integer.parseInt(numberBuffer.toString());
                        numberBuffer = new StringBuilder();
                    }
                    if (c == ',') {
                        commaCounter++;
                        if (commaCounter == 2) {
                            x = Integer.parseInt(numberBuffer.toString());
                        }
                    }
                    if (Character.isDigit(c)) {
                        numberBuffer.append(c);
                    }
                }
                numberBuffer = new StringBuilder();
                assert x != 0 || y != 0;
                if (x > DIMENSION_LIMIT || y > DIMENSION_LIMIT) {
                    throw new IllegalArgumentException("Dimension is higher than the Limit. X: %s, Y: %s, Limit: %s".formatted(y, x, DIMENSION_LIMIT));
                }
                continue;
            }

            for (char c : line.toCharArray()) {
                if (Character.isDigit(c)) numberBuffer.append(c);
                else {
                    int multiplier = 1;
                    if (!numberBuffer.isEmpty()) {
                        multiplier = Integer.parseInt(numberBuffer.toString());
                        numberBuffer = new StringBuilder();
                    }

                    if (c == '$') {
                        yIndex = 0;
                        xIndex += multiplier;
                        continue;
                    }
                    if (c == '!') {
                        return;
                    }

                    for (int i = 0; i < multiplier; i++) {
                        if (xIndex == x || yIndex == y) {
                            throw new IllegalArgumentException("Header with x: %s and y: %s does not match pattern.".formatted(y, x));
                        }
                        if (c != 'b') {
                            Coordinates offset = rotateOffset(xIndex, yIndex, x, y, rotation);
                            worldEditor.setCell(
                                    worldStartingCoordinates.x() + offset.x(),
                                    worldStartingCoordinates.y() + offset.y(),
                                    hitId);
                        }
                        yIndex++;
                    }
                }
            }
        }
        throw new IllegalArgumentException("No '!' symbol was found in the file");
    }

    private static Coordinates rotateOffset(int row, int col, int rows, int cols, byte rotation) {
        return switch (rotation) {
            case 0 -> new Coordinates(row, col);
            case 1 -> new Coordinates(col, rows - 1 - row);
            case 2 -> new Coordinates(rows - 1 - row, cols - 1 - col);
            case 3 -> new Coordinates(cols - 1 - col, row);
            default -> throw new IllegalArgumentException("Invalid rotation: " + rotation);
        };
    }
}
