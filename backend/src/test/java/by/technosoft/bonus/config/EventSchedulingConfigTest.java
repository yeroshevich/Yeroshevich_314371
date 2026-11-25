package by.technosoft.bonus.config;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;

import by.technosoft.bonus.service.EventProcessingService;
import by.technosoft.bonus.service.dto.BatchProcessingResult;

/** Автоматическая синхронизация: периодическая обработка очереди событий от внешних систем (UC4, UC13). */
@ExtendWith(MockitoExtension.class)
@DisplayName("EventSchedulingConfig — автоматическая обработка событий по расписанию")
class EventSchedulingConfigTest {

    @Mock private EventProcessingService eventProcessingService;

    @Test
    @DisplayName("FR13-3: по расписанию запускается пакетная обработка поступивших событий")
    void scheduledRunProcessesQueue() {
        when(eventProcessingService.processPending())
                .thenReturn(new BatchProcessingResult(3, 2, 1, 0, 0), new BatchProcessingResult(0, 0, 0, 0, 0));
        EventSchedulingConfig config = new EventSchedulingConfig(eventProcessingService);

        config.processPendingEvents();
        config.processPendingEvents();

        verify(eventProcessingService, times(2)).processPending();
    }

    @Test
    @DisplayName("FR13-3: расписание задаётся настройкой (интервал по умолчанию 1 минута), включается свойством")
    void scheduleIsConfigurable() throws Exception {
        Method run = EventSchedulingConfig.class.getMethod("processPendingEvents");
        Assertions.assertThat(run.getAnnotation(Scheduled.class).fixedDelayString())
                .isEqualTo("${bonus.event-scheduler.delay:PT1M}");
        Assertions.assertThat(EventSchedulingConfig.class.getAnnotation(ConditionalOnProperty.class).prefix())
                .isEqualTo("bonus.event-scheduler");
    }
}
