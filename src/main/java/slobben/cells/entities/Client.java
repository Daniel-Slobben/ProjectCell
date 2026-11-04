package slobben.cells.entities;

import lombok.Getter;
import lombok.Setter;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class Client {
    private final UUID clientId = UUID.randomUUID();
    // changed by client-updates while the tick loops over it
    private final Set<String> activeBlocks = ConcurrentHashMap.newKeySet();
    @Setter
    private boolean inError = true;
    @Setter
    private int blockLevel = 0;
    private long healthCheck = System.currentTimeMillis();

    public void resetHealthCheck() {
        healthCheck = System.currentTimeMillis();
    }

    public boolean isInError() {
        boolean toReturn = inError;
        inError = false;
        return toReturn;
    }
}
