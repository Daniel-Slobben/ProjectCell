package slobben.cells.entities.model;

import lombok.Getter;
import lombok.Setter;

public class BorderInfo {
    private final int blockSizeWithBorder;
    private final int blockSize;
    @Setter
    @Getter
    private boolean hasAliveCells = false;

    private final boolean[] topBorder;
    private final boolean[] bottomBorder;
    private final boolean[] leftBorder;
    private final boolean[] rightBorder;

    @Setter
    private boolean topRightCorner = false;
    @Setter
    private boolean topLeftCorner = false;
    @Setter
    private boolean bottomRightCorner = false;
    @Setter
    private boolean bottomLeftCorner = false;

    public BorderInfo(int blockSize) {
        this.blockSizeWithBorder =  blockSize + 2;
        this.blockSize = blockSize;
        this.topBorder = new boolean[blockSize];
        this.bottomBorder = new boolean[blockSize];
        this.leftBorder = new boolean[blockSize];
        this.rightBorder = new boolean[blockSize];
    }

    public void copyCells(Block block) {
        copyTopBorder(block.getCells());
        copyBottomBorder(block.getCells());
        copyLeftBorder(block.getCells());
        copyRightBorder(block.getCells());
    }

    private void copyTopBorder(boolean[][] destinationCells) {
        System.arraycopy(topBorder, 0, destinationCells[0], 1, blockSizeWithBorder - 2);
        destinationCells[0][0] = this.topLeftCorner;
        destinationCells[0][blockSizeWithBorder - 1] = this.topRightCorner;
    }

    private void copyBottomBorder(boolean[][] destinationCells) {
        System.arraycopy(bottomBorder, 0, destinationCells[blockSizeWithBorder - 1], 1, blockSizeWithBorder - 2);
        destinationCells[blockSizeWithBorder - 1][0] = this.bottomLeftCorner;
        destinationCells[blockSizeWithBorder - 1][blockSizeWithBorder - 1] = this.bottomRightCorner;
    }

    private void copyLeftBorder(boolean[][] destinationCells) {
        for (int i = 1; i < blockSizeWithBorder - 1; i++) {
            destinationCells[i][0] = leftBorder[i - 1];
        }
    }

    private void copyRightBorder(boolean[][] destinationCells) {
        for (int i = 1; i < blockSizeWithBorder - 1; i++) {
            destinationCells[i][blockSizeWithBorder - 1] = rightBorder[i - 1];
        }
    }

    public boolean[] setTopBorder(boolean[] cells) {
        System.arraycopy(cells, 1, topBorder, 0, blockSize);
        return topBorder;
    }

    public boolean[] setBottomBorder(boolean[] cells) {
        System.arraycopy(cells, 1, bottomBorder, 0, blockSize);
        return bottomBorder;
    }

    public void setLeftBorder(boolean[] cells) {
        System.arraycopy(cells, 0, leftBorder, 0, blockSize);
    }

    public void setRightBorder(boolean[] cells) {
        System.arraycopy(cells, 0, rightBorder, 0, blockSize);
    }
}
