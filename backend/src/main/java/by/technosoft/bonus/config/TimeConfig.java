package by.technosoft.bonus.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Источник текущего времени. Сервисы получают {@link Clock} через DI,
 * поэтому даты расчёта воспроизводимы в тестах (фиксированные часы).
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock(BonusProperties properties) {
        return Clock.system(properties.zone());
    }
}
