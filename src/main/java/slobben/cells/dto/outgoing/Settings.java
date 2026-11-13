package slobben.cells.dto.outgoing;

import slobben.cells.dto.internal.Coordinates;

public record Settings(int blockSize, Coordinates viewport, int targetSpeed) {
}
