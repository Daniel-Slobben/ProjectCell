package slobben.cells.dto.outgoing;

public record StatusResponse(Type type) {

    public enum Type {
        // the first /client-update of a session asked only for blocks that don't exist
        NO_BLOCKS
    }
}
