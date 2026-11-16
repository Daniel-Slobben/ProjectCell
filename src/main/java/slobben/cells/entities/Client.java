package slobben.cells.entities;

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
    private int blockLevel = 0;
    // set by the client's socket, read by the tick
    @Setter
    private volatile boolean sectors;
}
