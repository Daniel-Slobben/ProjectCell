package slobben.cells;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import slobben.cells.service.EnvironmentService;
import slobben.cells.service.RunnerService;

@SpringBootApplication
public class CellsApplication {

    static void main(String[] args) {
        ApplicationContext applicationContext = SpringApplication.run(CellsApplication.class, args);
        if (applicationContext.getBean(EnvironmentService.class).getRunMode().equals("AUTO")) {
            applicationContext.getBean(RunnerService.class).run();
        }
    }
}
