package by.technosoft.bonus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Точка входа серверной части TechnoSoft Bonus (монолит: API → бизнес-логика → доступ к данным).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class BonusApplication {

    public static void main(String[] args) {
        SpringApplication.run(BonusApplication.class, args);
    }
}
