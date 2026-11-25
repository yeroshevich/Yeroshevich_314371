package by.technosoft.bonus.config;

import java.time.Duration;
import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Настраиваемые параметры бизнес-логики (префикс {@code bonus} в application.yml).
 *
 * @param accrualLimit   лимит разового ручного начисления на сотрудника, баллы (UC5)
 * @param eventBatchSize сколько необработанных событий брать за один проход (UC4)
 * @param zone           часовой пояс, в котором считаются бизнес-даты
 * @param eventScheduler периодическая обработка событий
 */
@ConfigurationProperties(prefix = "bonus")
public record BonusProperties(
        @DefaultValue("1000") int accrualLimit,
        @DefaultValue("100") int eventBatchSize,
        @DefaultValue("Europe/Minsk") ZoneId zone,
        @DefaultValue EventScheduler eventScheduler) {

    /**
     * @param enabled включён ли планировщик
     * @param delay   пауза между проходами
     */
    public record EventScheduler(@DefaultValue("false") boolean enabled, @DefaultValue("PT1M") Duration delay) {
    }
}
