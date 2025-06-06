package slobben.Cells;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import slobben.Cells.service.EnvironmentService;
import slobben.Cells.service.RunnerService;

@SpringBootApplication
@EnableScheduling
@EnableMongoRepositories("slobben.Cells.database.repository")
public class CellsApplication {

    public static void main(String[] args) {
        ApplicationContext applicationContext = SpringApplication.run(CellsApplication.class, args);
        if (applicationContext.getBean(EnvironmentService.class).getRunMode().equals("AUTO")) {
            applicationContext.getBean(RunnerService.class).run();
        }
    }
}
