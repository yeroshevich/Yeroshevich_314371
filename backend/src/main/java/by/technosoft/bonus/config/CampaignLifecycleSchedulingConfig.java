package by.technosoft.bonus.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import by.technosoft.bonus.service.campaign.CampaignLifecycleService;
import by.technosoft.bonus.service.campaign.CampaignLifecycleService.StatusChange;

/**
 * Смена статусов бонусных кампаний по датам (UC9, исправление дефекта D-1): при старте приложения
 * (статусы могли устареть, пока оно было остановлено) и ежедневно в начале суток по {@code bonus.zone}.
 * Расписание — {@code bonus.campaign-lifecycle.cron}; отключается {@code bonus.campaign-lifecycle.enabled=false}.
 * Ошибка синхронизации не останавливает приложение: она записывается в лог, следующий запуск повторит попытку.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "bonus.campaign-lifecycle", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class CampaignLifecycleSchedulingConfig {

    private static final Logger log = LoggerFactory.getLogger(CampaignLifecycleSchedulingConfig.class);

    private final CampaignLifecycleService campaignLifecycleService;

    public CampaignLifecycleSchedulingConfig(CampaignLifecycleService campaignLifecycleService) {
        this.campaignLifecycleService = campaignLifecycleService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void synchronizeOnStartup() {
        synchronize("при старте приложения");
    }

    @Scheduled(cron = "${bonus.campaign-lifecycle.cron:0 0 0 * * *}", zone = "${bonus.zone:Europe/Minsk}")
    public void synchronizeDaily() {
        synchronize("по расписанию");
    }

    private void synchronize(String trigger) {
        try {
            List<StatusChange> changes = campaignLifecycleService.synchronizeToday();
            log.info("Синхронизация статусов кампаний {}: изменено {}", trigger, changes.size());
        } catch (RuntimeException e) {
            log.error("Синхронизация статусов кампаний {} не выполнена", trigger, e);
        }
    }
}
