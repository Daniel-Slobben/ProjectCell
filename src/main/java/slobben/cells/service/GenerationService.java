package slobben.cells.service;

import slobben.cells.entities.model.Block;

import java.util.LinkedList;

public class GenerationService {

    @SuppressWarnings({"java:S3776", "java:S135"})
    public static void setNextState(Block block) {
        final int blockSizeWithBorder = block.getCells().length;
        final int blockSize = blockSizeWithBorder - 2;

        byte[][] heatmap = new byte[blockSizeWithBorder][blockSizeWithBorder];
        for (int x = 0; x < blockSizeWithBorder; x++) {
            for (int y = 0; y < blockSizeWithBorder; y++) {
                if (!block.getCells()[x][y]) continue;
                for (int i = -1; i <= 1; i++) {
                    for (int j = -1; j <= 1; j++) {
                        if (i == 0 && j == 0) continue;
                        if (x + i < 0 || y + j < 0 || x + i >= blockSizeWithBorder || y + j >= blockSizeWithBorder) continue;
                        heatmap[x + i][y + j]++;
                    }
                }
            }
        }

        LinkedList<Byte> deltas = new LinkedList<>();


        for (int x = 1; x < blockSize + 1; x++) {
            for (int y = 1; y < blockSize + 1; y++) {
                // If cell was dead
                if (!block.getCells()[x][y]) {
                    if (heatmap[x][y] == 3) {
                        // now alive
                        block.getCells()[x][y] = true;
                        setDeltaOperation((byte) 0b01_00000, deltas);
                    } else {
                        // still dead
                        setDeltaOperation((byte) 0b00_00000, deltas);
                    }
                }
                // If cell was alive
                else {
                    if (!(heatmap[x][y] == 2 || heatmap[x][y] == 3)) {
                        // now dead
                        block.getCells()[x][y] = false;
                        setDeltaOperation((byte) 0b11_00000, deltas);
                    } else {
                        // still alive
                        setDeltaOperation((byte) 0b10_00000, deltas);
                    }
                }
            }
        }
    }

    /**
     * first two bits for operation
     * 00 -- cell was dead, still dead
     * 01 -- cell was dead, now alive
     * 10 -- cell was alive, still alive
     * 11 -- cell was alive, now dead
     * <p>
     * 6 least significant bits for how many in a row a cell has the same operation
     * max = 64 times, starting at 0 (so 000000 means the operation only applies to the current cell
     *
     * @param newOperation byte with only the two most significant bits (ex: (byte) 0b11_000000
     * @param deltas       LinkedList with all previous bytes
     */
    private static void setDeltaOperation(byte newOperation, LinkedList<Byte> deltas) {
        int lastDeltaOperation = 0b0010_000;
        if (!deltas.isEmpty()) {
            lastDeltaOperation = deltas.peekLast() & 0x11_000000;
        }
        if (lastDeltaOperation != newOperation || deltas.isEmpty()) {
            deltas.addLast(newOperation);
        } else {
            byte delta = deltas.pollLast();
            int operationAmount = delta & 0b00111111;

            if (operationAmount != 63) {
                operationAmount++;
                deltas.add((byte) (newOperation | operationAmount));
            }
        }
    }

}
