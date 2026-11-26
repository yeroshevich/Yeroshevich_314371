package by.technosoft.bonus.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;

import by.technosoft.bonus.domain.CampaignStatus;
import by.technosoft.bonus.service.campaign.CampaignLifecycleService;
import by.technosoft.bonus.service.campaign.CampaignLifecycleService.StatusChange;

/** Запуск смены статусов кампаний: при старте приложения и ежедневно (исправление D-1). */
@ExtendWith(MockitoExtension.class)
@DisplayName("CampaignLifecycleSchedulingConfig — статусы кампаний при старте и по расписанию")
class CampaignLifecycleSchedulingConfigTest {

    @Mock private CampaignLifecycleService campaignLifecycleService;

    @Test
    @DisplayName("FR9-2, FR9-3: статусы кампаний обновляются при старте приложения и ежедневным заданием")
    void startupAndDailyRunsSynchronize() {
        when(campaignLifecycleService.synchronizeToday()).thenReturn(
                List.of(new StatusChange(2L, "Неделя наставничества", CampaignStatus.PLANNED, CampaignStatus.ACTIVE)),
                List.of());
        CampaignLifecycleSchedulingConfig config = new CampaignLifecycleSchedulingConfig(campaignLifecycleService);

        config.synchronizeOnStartup();
        config.synchronizeDaily();

        verify(campaignLifecycleService, times(2)).synchronizeToday();
    }

    @Test
    @DisplayName("FR9-2, FR9-3: ошибка синхронизации не останавливает приложение — повтор при следующем запуске")
    void failureDoesNotStopApplication() {
        when(campaignLifecycleService.synchronizeToday()).thenThrow(new IllegalStateException("БД недоступна"));
        CampaignLifecycleSchedulingConfig config = new CampaignLifecycleSchedulingConfig(campaignLifecycleService);

        assertThatCode(config::synchronizeOnStartup).doesNotThrowAnyException();
        assertThatCode(config::synchronizeDaily).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("FR9-2, FR9-3: запуск при готовности приложения и ежедневно в 00:00 по bonus.zone; по умолчанию включён")
    void triggersAreConfigured() throws Exception {
        Method startup = CampaignLifecycleSchedulingConfig.class.getMethod("synchronizeOnStartup");
        assertThat(startup.getAnnotation(EventListener.class).value()).containsExactly(ApplicationReadyEvent.class);

        Scheduled daily = CampaignLifecycleSchedulingConfig.class.getMethod("synchronizeDaily")
                .getAnnotation(Scheduled.class);
        assertThat(daily.cron()).isEqualTo("${bonus.campaign-lifecycle.cron:0 0 0 * * *}");
        assertThat(daily.zone()).isEqualTo("${bonus.zone:Europe/Minsk}");

        ConditionalOnProperty condition = CampaignLifecycleSchedulingConfig.class
                .getAnnotation(ConditionalOnProperty.class);
        assertThat(condition.prefix()).isEqualTo("bonus.campaign-lifecycle");
        assertThat(condition.name()).containsExactly("enabled");
        assertThat(condition.matchIfMissing()).isTrue();
    }
}
