package slobben.cells.dto.incoming;

public record ClientUpdateRequest(int minX, int maxX, int minY, int maxY, Boolean sectors, String[] resend) {

    public ClientUpdateRequest {
        if (maxX < minX || maxY < minY) {
            throw new IllegalArgumentException("Upside-down view %s..%s, %s..%s".formatted(minX, maxX, minY, maxY));
        }
    }

    public boolean watchesSectors() {
        return Boolean.TRUE.equals(sectors);
    }

    public String[] resendKeys() {
        return resend == null ? new String[0] : resend;
    }

    public long blockCount() {
        return ((long) maxX - minX + 1) * ((long) maxY - minY + 1);
    }
}
