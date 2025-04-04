package slobben.Cells;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableMongoRepositories("slobben.Cells.database.repository")
public class CellsApplication {

	public static void main(String[] args) {
		SpringApplication.run(CellsApplication.class, args);
	}
}
