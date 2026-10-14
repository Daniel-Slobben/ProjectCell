package slobben.cells.dto.outgoing;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EncodedBlockType {
    FULL(0),
    BORDER(1);

    // the type byte in a BlockFrame
    private final int code;
}
