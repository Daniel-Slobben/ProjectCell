package slobben.cells.dto.outgoing;

public record HealthCheckResponse(HEALTH_CHECK_TYPE type) {

    public enum HEALTH_CHECK_TYPE {
        HEALTH_ACK,
        SESSION_DEAD
    }
}
