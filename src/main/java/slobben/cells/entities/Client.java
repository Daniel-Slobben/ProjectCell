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
    private final Set<String> activeBlocks = ConcurrentHashMap.newKeySet();
    @Setter
    private volatile ClientUpdateRequest lastUpdateRequest;
    @Setter
    private int blockLevel = 0;
    // set by the client's socket, read by the tick
    @Setter
    private volatile boolean sectors;
    // the tick of the sector period on which it gets its sectors
    @Setter
    private int sectorPhase;
}
