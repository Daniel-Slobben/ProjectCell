package slobben.cells.service;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExecutorService {

    @SneakyThrows
    public void executeTasksParallel(Set<Runnable> tasks, String name) {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Runnable task : tasks) {
                executor.execute(() -> {
                    try {
                        task.run();
                    } catch (Exception e) {
                        log.error("Task with Name {} gave exception!", name, e);
                    }
                });
            }
        }
    }
}
