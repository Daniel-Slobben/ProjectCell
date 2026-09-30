package slobben.cells.entities.model;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
public class Client {
    private final UUID clientId = UUID.randomUUID();
    @Getter
    private final List<String> activeBlocks = new ArrayList<>();
    @Setter
    private boolean inError = false;
    @Setter
    private int blockLevel = 0;
    @Getter
    private int healthCheck = 0;

    public void incrementHealthCheck() {
        healthCheck++;
    }

    public void resetHealthCheck() {
        healthCheck = 0;
    }

    public boolean isInError() {
        boolean toReturn = inError;
        inError = false;
        return toReturn;
    }
}
