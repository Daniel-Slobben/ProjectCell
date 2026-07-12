package slobben.cells.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import slobben.cells.entities.Pattern;

import java.io.*;
import java.util.Random;

@Slf4j
public class RleReader {
    public Pattern readRandomPatternFromCategoryWithSize(String category, int limit) throws IOException {
        // dangerous and inefficient but effective
        Pattern pattern = readRandomPatternFromCategory(category);
        if (pattern.x() > limit || pattern.y() > limit) {
            return readRandomPatternFromCategoryWithSize(category, limit);
        }
        return pattern;
    }
    private static final Random random = new Random();
    private static final int DIMENSION_LIMIT = 50_000;

    public Pattern readPatternFromFilename(String name) throws IOException {
        name = name.replace(".rle", "");
        File file = new File("src/main/resources/patterns/" + name + ".rle");

        return getPatternFromResource(name, new FileInputStream(file.getPath()));
    }

    public enum PatternCategories {
        GROWTH_PATTERNS("growth-patterns"),
        OSCILLATORS("oscillators");

        public final String directory;

        PatternCategories(String name) {
            this.directory = name;
        }
    }

    public Pattern readRandomPatternFromCategory(String category) throws IOException {
        Resource resource = new ClassPathResource("src/main/resources/patterns/" + category);
        File file = resource.getFile();
        int chosenPattern = random.nextInt(0, file.listFiles().length);
        return getPatternFromResource(file.listFiles()[chosenPattern].getPath(), new FileInputStream(file.listFiles()[chosenPattern].getPath()));
    }

    private static Pattern getPatternFromResource(String name, InputStream inputStream) throws IOException {
        log.info("Reading file {}", name);
        int x = 0;
        int y = 0;
        boolean[][] matrix = null;

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

            // not a comment. not coordinates. it must be the start of encoding.
            if (matrix == null) {
                matrix = new boolean[x][y];
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
                        numberBuffer = new StringBuilder();
                        continue;
                    }
                    if (c == '!') {
                        return new Pattern(name, x, y, matrix);
                    }

                    for (int i = 0; i < multiplier; i++) {
                        if (xIndex == x || yIndex == y) {
                            throw new IllegalArgumentException("Header with x: %s and y: %s does not match pattern.".formatted(y, x));
                        }
                        matrix[xIndex][yIndex] = c != 'b';
                        yIndex++;
                    }
                }
            }
        }
        throw new IllegalArgumentException("No '!' symbol was found in the file");
    }
}
