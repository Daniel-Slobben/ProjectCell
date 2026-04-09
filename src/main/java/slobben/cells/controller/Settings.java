package slobben.cells.controller;

import java.util.UUID;

public record Settings(int blockSize, UUID clientId, int x, int y) {
}
