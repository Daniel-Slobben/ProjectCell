package slobben.cells.enums;

public enum PatternCategories {
        GROWTH_PATTERNS("growth-patterns"),
        OSCILLATORS("oscillators");

        public final String directory;

        PatternCategories(String name) {
            this.directory = name;
        }
    }
