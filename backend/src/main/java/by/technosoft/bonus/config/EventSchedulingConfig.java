package by.technosoft.bonus.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import by.technosoft.bonus.service.EventProcessingService;
import by.technosoft.bonus.service.dto.BatchProcessingResult;

/**
 * Периодический запуск пакетной обработки событий (UC4: «событие/расписание»).
 * Включается свойством {@code bonus.event-scheduler.enabled=true}.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "bonus.event-scheduler", name = "enabled", havingValue = "true")
public class EventSchedulingConfig {

    private static final Logger log = LoggerFactory.getLogger(EventSchedulingConfig.class);

    private final EventProcessingService eventProcessingService;

    public EventSchedulingConfig(EventProcessingService eventProcessingService) {
        this.eventProcessingService = eventProcessingService;
    }

    @Scheduled(fixedDelayString = "${bonus.event-scheduler.delay:PT1M}")
    public void processPendingEvents() {
        BatchProcessingResult result = eventProcessingService.processPending();
        if (result.total() > 0) {
            log.info("Обработано событий: {}", result);
        }
    }
}
