package slobben.cells.service.workers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import slobben.cells.service.ServerInfoService;

@Slf4j
public abstract class Worker {

    protected ServerInfoService serverInfoService;

    @Autowired
    void setServerInfoService(ServerInfoService serverInfoService) {
        this.serverInfoService = serverInfoService;
    }

    public abstract void execute();

    public abstract String getName();

    public void tic() {
        long start = System.nanoTime();
        execute();
        long nanos = System.nanoTime() - start;
        serverInfoService.recordWorkTime(getClass().getSimpleName(), nanos);
        log.info("Task {} took {}ms.", getName(), nanos / 1_000_000);
    }
}
