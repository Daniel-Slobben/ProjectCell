package slobben.cells.service;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExecutorService {

    private static final int TIMEOUT_SECONDS = 5;

    @SneakyThrows
    public void executeTasksParallel(Set<Runnable> tasks, String name) {
        try (var pool = ForkJoinPool.commonPool()) {
            tasks.forEach(pool::submit);
            pool.shutdown();
            if (pool.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new TimeoutException(TIMEOUT_SECONDS + " seconds timeout reached for task: ." + name);
            }
        }

    }
}
