package slobben.cells.entities;

import slobben.cells.dto.incoming.ClientUpdateRequest;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Getter
@RequiredArgsConstructor
public class Client {
    private final String sessionId;
    // the blocks in view while it watches cells; watching sectors it has only the view
    private final Set<String> activeBlocks = ConcurrentHashMap.newKeySet();
    // the last ClientUpdateRequest, null before the first
    @Setter
    private volatile ClientUpdateRequest view;
    @Setter
    private int blockLevel = 0;
    // set by the client's socket, read by the tick
    @Setter
    private volatile boolean sectors;
}
