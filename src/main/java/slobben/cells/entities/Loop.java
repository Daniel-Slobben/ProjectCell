package slobben.cells.entities;

import java.util.Arrays;
import java.util.List;

/**
 * The generations of a hibernating block, which come back after the last one. A phase keeps what the workers ask of
 * a block: its encodings, its sector counts, its edges for the neighbours and the borders it got from them.
 */
public final class Loop {
    private final List<Phase> phases;
    private final Outline received;
    private int phase;

    public Loop(List<Phase> phases, int size) {
        this.phases = List.copyOf(phases);
        this.received = Outline.empty(size);
    }

    public Phase phase() {
        return phases.get(phase);
    }

    public int period() {
        return phases.size();
    }

    public void next() {
        phase = (phase + 1) % phases.size();
    }

    // the borders the neighbours wrote in this tick
    public Outline received() {
        return received;
    }

    public record Phase(byte[] full, byte[] border, byte[] sectors, short[][] sectorCounts, Outline edges,
                        Outline borders) {
    }

    /**
     * Two rows (packed, like the rows of the cells) and two columns (bit x for row x) of a block: rows 1 and size and
     * columns 1 and size for its edges, rows 0 and size + 1 and columns 0 and size + 1 for its borders.
     */
    public record Outline(long[] top, long[] bottom, long[] left, long[] right) {

        static Outline empty(int size) {
            int words = (size + 2 + 63) >>> 6;
            int columnWords = (size + 64) >>> 6;
            return new Outline(new long[words + 2], new long[words + 2], new long[columnWords], new long[columnWords]);
        }

        public long[] row(int row) {
            return row == 0 ? top : bottom;
        }

        public long[] column(int column) {
            return column == 0 ? left : right;
        }

        public void clear() {
            Arrays.fill(top, 0L);
            Arrays.fill(bottom, 0L);
            Arrays.fill(left, 0L);
            Arrays.fill(right, 0L);
        }

        public boolean same(Outline other) {
            return Arrays.equals(top, other.top) && Arrays.equals(bottom, other.bottom)
                    && Arrays.equals(left, other.left) && Arrays.equals(right, other.right);
        }
    }
}
