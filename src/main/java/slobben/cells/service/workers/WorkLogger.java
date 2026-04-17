package slobben.cells.service.workers;

import lombok.extern.slf4j.Slf4j;

@Slf4j
final class WorkLogger {
    static void logWork(long startTime, Worker worker) {
        log.info("Task {} took {}ms.", worker.getName(), System.currentTimeMillis() - startTime);
    }
}
