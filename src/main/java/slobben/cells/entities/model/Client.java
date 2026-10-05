package slobben.cells.entities.model;

import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Getter
public class Client {
    private final UUID clientId = UUID.randomUUID();
    private final Set<String> activeBlocks = new HashSet<>();
    @Setter
    private boolean inError = false;
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
