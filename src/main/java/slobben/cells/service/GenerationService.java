package slobben.cells.service;

import slobben.cells.entities.model.Block;

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

        int deltaIndex = -1;
        byte[] deltas = new byte[blockSize * blockSize];

        for (int x = 1; x < blockSize + 1; x++) {
            for (int y = 1; y < blockSize + 1; y++) {
                // If cell was dead
                if (!block.getCells()[x][y]) {
                    if (heatmap[x][y] == 3) {
                        // now alive
                        block.getCells()[x][y] = true;
                        deltaIndex = setDeltaOperation(true, deltas, deltaIndex);
                    } else {
                        deltaIndex = setDeltaOperation(false, deltas, deltaIndex);
                    }
                }
                // If cell was alive
                else {
                    if (!(heatmap[x][y] == 2 || heatmap[x][y] == 3)) {
                        // now dead
                        block.getCells()[x][y] = false;
                        deltaIndex = setDeltaOperation(true, deltas, deltaIndex);
                    } else {
                        deltaIndex = setDeltaOperation(false, deltas, deltaIndex);
                    }
                }
            }
        }
        block.addByteArrayToDelta(deltas);
    }

    /**
     * first bit for operation
     * 0 -- not changed
     * 1 -- changed
     * -
     * 7 least significant bits for how many in a row a cell has the same operation
     * max = 128 times, starting at 1 (so 0_0000001 means the operation only applies to the current cell
     */
    private static int setDeltaOperation(boolean cellChanged, byte[] deltas, int deltaIndex) {
        byte lastDeltaOperation;
        boolean noLastOperation = deltaIndex == -1;
        boolean switchToOtherOperation = false;

        byte cellChangedOperation = cellChanged ? (byte) 0b1_000_0000 : (byte) 0b0_000_0000;

        if (!noLastOperation) {
            lastDeltaOperation = (byte) (deltas[deltaIndex] & 0b1_000_0000);
            if (lastDeltaOperation != cellChangedOperation) {
                switchToOtherOperation = true;
            }
        }

        if (noLastOperation || switchToOtherOperation) {
            deltas[++deltaIndex] = (byte) (cellChangedOperation | 0b0000_0001);
        } else {
            int operationAmount = deltas[deltaIndex] & 0b0_111_1111;

            if (operationAmount != 0b111_1111) {
                operationAmount++;
                deltas[deltaIndex] = (byte) (cellChangedOperation | operationAmount);
            } else {
                deltas[++deltaIndex] = (byte) (cellChangedOperation | 0b0000_0001);
            }
        }
        return deltaIndex;
    }
}
