package slobben.cells.entities.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Getter
public class Client {
    private final UUID clientId = UUID.randomUUID();
    private final List<String> activeBlocks = new CopyOnWriteArrayList<>();
    @Setter
    private boolean inError = false;
    @Setter
    private int blockLevel = 0;
    private long healthCheck = 0;

    public void resetHealthCheck() {
        healthCheck = System.currentTimeMillis();
    }

    public boolean isInError() {
        boolean toReturn = inError;
        inError = false;
        return toReturn;
    }
}
