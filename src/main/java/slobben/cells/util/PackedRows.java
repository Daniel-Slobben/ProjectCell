package slobben.cells.util;

import java.util.Arrays;

/**
 * Bit rows of a block: one long[] per row x, column y is a bit.
 * Word i + 1 holds columns 64i .. 64i + 63, words 0 and words + 1 are always-zero padding.
 */
public final class PackedRows {

    private PackedRows() {
    }

    public static int words(int size) {
        return (size + 2 + 63) >>> 6;
    }

    public static long[][] create(int size) {
        return new long[size + 2][words(size) + 2];
    }

    // per word: the bits of columns 1 .. size, so the border columns 0 and size + 1 don't count
    public static long[] innerMask(int size) {
        long[] mask = new long[words(size) + 2];
        for (int col = 1; col <= size; col++) {
            mask[(col >>> 6) + 1] |= 1L << col;
        }
        return mask;
    }

    public static boolean get(long[] row, int col) {
        return (row[(col >>> 6) + 1] >>> col & 1) != 0;
    }

    public static void set(long[] row, int col) {
        row[(col >>> 6) + 1] |= 1L << col;
    }

    public static void clear(long[] row, int col) {
        row[(col >>> 6) + 1] &= ~(1L << col);
    }

    // column col of rows 1 .. size as bits, bit x for row x
    public static long[] column(long[][] rows, int col) {
        long[] column = new long[(rows.length + 62) >>> 6];
        for (int x = 1; x < rows.length - 1; x++) {
            column[x >>> 6] |= (rows[x][(col >>> 6) + 1] >>> col & 1L) << x;
        }
        return column;
    }

    public static long[][] pack(boolean[][] cells) {
        long[][] packed = create(cells.length - 2);
        boolean[] empty = new boolean[cells.length];
        for (int x = 0; x < cells.length; x++) {
            boolean[] row = cells[x];
            // most of a new block is empty: mismatch finds the next cell, it compares many cells at once
            for (int from = 0; from < row.length; ) {
                int found = Arrays.mismatch(row, from, row.length, empty, from, row.length);
                if (found < 0) break;
                int to = Math.min(row.length, ((from + found) | 63) + 1);
                packRange(row, packed[x], from + found, to);
                from = to;
            }
        }
        return packed;
    }

    public static long[][] copy(long[][] rows) {
        long[][] copy = new long[rows.length][];
        for (int x = 0; x < rows.length; x++) {
            copy[x] = rows[x].clone();
        }
        return copy;
    }

    /**
     * ORs cells[from..to) into the packed row.
     */
    public static void packRange(boolean[] cells, long[] packed, int from, int to) {
        int y = from;
        while (y < to) {
            int end = Math.min(to, ((y >>> 6) + 1) << 6);
            long word = 0;
            for (int b = y; b < end; b++) {
                word |= (cells[b] ? 1L : 0L) << b;
            }
            packed[(y >>> 6) + 1] |= word;
            y = end;
        }
    }
}
