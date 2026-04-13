package slobben.cells.dto;

import slobben.cells.enums.BlockType;

public record EncodedBlock(int x, int y, String encodedCells, BlockType blockType) {
}
