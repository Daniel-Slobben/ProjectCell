package slobben.cells.service.workers;

public interface Worker {
    void execute();

    String getName();

    default void tic() {
        long timer = System.currentTimeMillis();
        this.execute();
        WorkLogger.logWork(timer, this);
    }
}
