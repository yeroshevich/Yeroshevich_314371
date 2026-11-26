package by.technosoft.bonus.config;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;

import by.technosoft.bonus.service.EventProcessingService;
import by.technosoft.bonus.service.campaign.CampaignLifecycleService;
import by.technosoft.bonus.service.dto.BatchProcessingResult;

/** Автоматическая синхронизация: периодическая обработка очереди событий от внешних систем (UC4, UC13). */
@ExtendWith(MockitoExtension.class)
@DisplayName("EventSchedulingConfig — автоматическая обработка событий по расписанию")
class EventSchedulingConfigTest {

    @Mock private EventProcessingService eventProcessingService;
    @Mock private CampaignLifecycleService campaignLifecycleService;

    @Test
    @DisplayName("FR13-3: по расписанию запускается пакетная обработка поступивших событий")
    void scheduledRunProcessesQueue() {
        when(eventProcessingService.processPending())
                .thenReturn(new BatchProcessingResult(3, 2, 1, 0, 0), new BatchProcessingResult(0, 0, 0, 0, 0));
        EventSchedulingConfig config = new EventSchedulingConfig(eventProcessingService, campaignLifecycleService);

        config.processPendingEvents();
        config.processPendingEvents();

        verify(eventProcessingService, times(2)).processPending();
    }

    @Test
    @DisplayName("FR9-2, FR9-3: перед автоматической обработкой событий статусы кампаний приводятся к текущей дате (D-1)")
    void campaignStatusesAreSynchronizedBeforeProcessing() {
        when(eventProcessingService.processPending()).thenReturn(new BatchProcessingResult(1, 1, 0, 0, 0));
        EventSchedulingConfig config = new EventSchedulingConfig(eventProcessingService, campaignLifecycleService);

        config.processPendingEvents();

        InOrder order = inOrder(campaignLifecycleService, eventProcessingService);
        order.verify(campaignLifecycleService).synchronizeToday();
        order.verify(eventProcessingService).processPending();
    }

    @Test
    @DisplayName("FR9-2: если статусы кампаний обновить не удалось, события не обрабатываются со старыми статусами")
    void processingIsSkippedWhenSynchronizationFails() {
        when(campaignLifecycleService.synchronizeToday()).thenThrow(new IllegalStateException("БД недоступна"));
        EventSchedulingConfig config = new EventSchedulingConfig(eventProcessingService, campaignLifecycleService);

        Assertions.assertThatThrownBy(config::processPendingEvents).isInstanceOf(IllegalStateException.class);

        verify(eventProcessingService, never()).processPending();
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
