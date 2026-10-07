package slobben.cells.dto.internal;

import lombok.Builder;

@Builder
public record Pattern(String name, int x, int y, boolean[][] matrix) {

}
