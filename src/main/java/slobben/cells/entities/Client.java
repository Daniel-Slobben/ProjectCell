package slobben.cells.entities;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// One per STOMP session: a client that reconnects is a new client.
@Getter
@RequiredArgsConstructor
public class Client {
    private final String sessionId;
    private final Set<String> activeBlocks = ConcurrentHashMap.newKeySet();
    @Setter
    private boolean inError = true;
    @Setter
    private int blockLevel = 0;

    public boolean isInError() {
        boolean toReturn = inError;
        inError = false;
        return toReturn;
    }
}
