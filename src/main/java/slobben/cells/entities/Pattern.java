package slobben.cells.entities;

import lombok.Builder;

@Builder
public record Pattern(String name, int x, int y, boolean[][] matrix) {

}
