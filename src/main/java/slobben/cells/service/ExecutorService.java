package slobben.cells.service;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExecutorService {

    @Value("${cells.threads}")
    private int threads;

    @SneakyThrows
    public void executeTasksParallel(Set<Runnable> tasks) {
        java.util.concurrent.ExecutorService executor = Executors.newFixedThreadPool(threads);

        tasks.forEach(executor::execute);
        executor.shutdown();

        if (!executor.awaitTermination(120, TimeUnit.SECONDS)) {
            log.warn("Executor did not shut down cleanly within timeout.");
        }
    }
}
