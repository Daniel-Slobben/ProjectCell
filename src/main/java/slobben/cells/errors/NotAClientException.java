package slobben.cells.errors;

public class NotAClientException extends RuntimeException {
    public NotAClientException(String message) {
        super(message);
    }
}
