package slobben.Cells.entities.model;

import lombok.Getter;
import lombok.Setter;

public class BorderInfo {
    private final int blockSizeWithBorder;
    @Setter
    @Getter
    private boolean hasAliveCells = false;
    private boolean[] topBorder;
    private boolean[] bottomBorder;

    // Excluding corner cells
    private boolean[] leftBorder;
    private boolean[] rightBorder;

    public BorderInfo(int blockSizeWithBorder) {
        this.blockSizeWithBorder = blockSizeWithBorder;
        this.topBorder = new boolean[blockSizeWithBorder];
        this.bottomBorder = new boolean[blockSizeWithBorder];
        this.leftBorder = new boolean[blockSizeWithBorder - 2];
        this.rightBorder = new boolean[blockSizeWithBorder - 2];
    }

    public void copyCells(Block block) {
        copyTopBorder(block.getCells());
        copyBottomBorder(block.getCells());
        copyLeftBorder(block.getCells());
        copyRightBorder(block.getCells());
    }

    private void copyTopBorder(boolean[][] destinationCells) {
        System.arraycopy(topBorder, 0, destinationCells[0], 0, blockSizeWithBorder);
    }

    private void copyBottomBorder(boolean[][] destinationCells) {
        System.arraycopy(bottomBorder, 0, destinationCells[blockSizeWithBorder - 1], 0, blockSizeWithBorder);
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
        System.arraycopy(cells, 1, topBorder, 1, blockSizeWithBorder - 1);
        return topBorder;
    }

    public boolean[] setBottomBorder(boolean[] cells) {
        System.arraycopy(cells, 1, bottomBorder, 1, blockSizeWithBorder - 1);
        return bottomBorder;
    }

    public void setLeftBorder(boolean[] cells) {
        System.arraycopy(cells, 1, leftBorder, 0, blockSizeWithBorder - 2);
    }

    public void setRightBorder(boolean[] cells) {
        System.arraycopy(cells, 1, rightBorder, 0, blockSizeWithBorder - 2);
    }

    public void setTopLeftCorner(boolean cell) {
        topBorder[0] = cell;
    }

    public void setTopRightCorner(boolean cell) {
        topBorder[topBorder.length - 1] = cell;
    }

    public void setBottomLeftCorner(boolean cell) {
        bottomBorder[0] = cell;
    }

    public void setBottomRightCorner(boolean cell) {
        bottomBorder[bottomBorder.length - 1] = cell;
    }
}
