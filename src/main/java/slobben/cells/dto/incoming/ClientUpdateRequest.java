package slobben.cells.dto.incoming;

/**
 * The blocks a client shows, minX to maxX and minY to maxY inclusive, as cells or as sectors. resend asks again for
 * the full blocks of blocks in view it could not decode.
 */
public record ClientUpdateRequest(int minX, int maxX, int minY, int maxY, Boolean sectors, String[] resend) {

    public boolean watchesSectors() {
        return Boolean.TRUE.equals(sectors);
    }

    public String[] resendKeys() {
        return resend == null ? new String[0] : resend;
    }

    // 0 for a range that is upside down
    public long blockCount() {
        return Math.max(0, (long) maxX - minX + 1) * Math.max(0, (long) maxY - minY + 1);
    }
}
