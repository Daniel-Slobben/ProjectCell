package slobben.Cells.entities.model;

import lombok.Getter;
import lombok.Setter;

public class BorderInfo {
    private final int blockSizeWithBorder;
    @Setter
    @Getter
    private boolean hasAliveCells = false;
    private byte[] topBorder;
    private byte[] bottomBorder;

    // Excluding corner cells
    private byte[] leftBorder;
    private byte[] rightBorder;

    public BorderInfo(int blockSizeWithBorder) {
        this.blockSizeWithBorder = blockSizeWithBorder;
        this.topBorder = new byte[blockSizeWithBorder];
        this.bottomBorder = new byte[blockSizeWithBorder];
        this.leftBorder = new byte[blockSizeWithBorder - 2];
        this.rightBorder = new byte[blockSizeWithBorder - 2];
    }

    public void copyCells(Block block) {
        copyTopBorder(block.getCells());
        copyBottomBorder(block.getCells());
        copyLeftBorder(block.getCells());
        copyRightBorder(block.getCells());
    }

    private void copyTopBorder(byte[][] destinationCells) {
        System.arraycopy(topBorder, 0, destinationCells[0], 0, blockSizeWithBorder);
    }

    private void copyBottomBorder(byte[][] destinationCells) {
        System.arraycopy(bottomBorder, 0, destinationCells[blockSizeWithBorder - 1], 0, blockSizeWithBorder);
    }

    private void copyLeftBorder(byte[][] destinationCells) {
        for (int i = 1; i < blockSizeWithBorder - 1; i++) {
            destinationCells[i][0] = leftBorder[i - 1];
        }
    }

    private void copyRightBorder(byte[][] destinationCells) {
        for (int i = 1; i < blockSizeWithBorder - 1; i++) {
            destinationCells[i][blockSizeWithBorder - 1] = rightBorder[i - 1];
        }
    }

    public byte[] setTopBorder(byte[] cells) {
        System.arraycopy(cells, 1, topBorder, 1, blockSizeWithBorder - 1);
        return topBorder;
    }

    public byte[] setBottomBorder(byte[] cells) {
        System.arraycopy(cells, 1, bottomBorder, 1, blockSizeWithBorder - 1);
        return bottomBorder;
    }

    public void setLeftBorder(byte[] cells) {
        System.arraycopy(cells, 1, leftBorder, 0, blockSizeWithBorder - 2);
    }

    public void setRightBorder(byte[] cells) {
        System.arraycopy(cells, 1, rightBorder, 0, blockSizeWithBorder - 2);
    }

    public void setTopLeftCorner(byte cell) {
        topBorder[0] = cell;
    }

    public void setTopRightCorner(byte cell) {
        topBorder[topBorder.length - 1] = cell;
    }

    public void setBottomLeftCorner(byte cell) {
        bottomBorder[0] = cell;
    }

    public void setBottomRightCorner(byte cell) {
        bottomBorder[bottomBorder.length - 1] = cell;
    }
}
