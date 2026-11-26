package by.technosoft.bonus.service.campaign;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import by.technosoft.bonus.domain.Campaign;
import by.technosoft.bonus.domain.CampaignStatus;
import by.technosoft.bonus.repository.CampaignRepository;
import by.technosoft.bonus.service.audit.AuditTrail;

/**
 * Жизненный цикл бонусной кампании по датам (UC9, FR9-2, FR9-3; исправление дефекта D-1).
 * <ul>
 *     <li>«Запланирована» → «Активна», когда наступила дата начала: {@code startDate ≤ today ≤ endDate};</li>
 *     <li>«Запланирована» / «Активна» → «Завершена», когда прошла дата окончания: {@code endDate < today}.</li>
 * </ul>
 * Кампании «Требуется утверждение» (бюджет не выделен) и «Завершена» не меняются. Обратных переходов нет.
 * Каждая смена статуса записывается в журнал аудита от имени системы. Повторный запуск в тот же день
 * ничего не меняет (идемпотентность); при одновременном запуске переход выполняет одна транзакция
 * (условное обновление {@link CampaignRepository#updateStatus}).
 * <p>
 * Запускается ежедневно и при старте приложения ({@code config.CampaignLifecycleSchedulingConfig}),
 * перед автоматической обработкой событий ({@code config.EventSchedulingConfig}) и перед чтением
 * списка кампаний ({@code service.query.CampaignQueryService}).
 */
@Service
public class CampaignLifecycleService {

    /** Действие в журнале аудита. */
    public static final String AUDIT_ACTION = "Изменение статуса кампании";

    /** Статусы, которые меняются по датам. */
    static final Set<CampaignStatus> DATE_DRIVEN = EnumSet.of(CampaignStatus.PLANNED, CampaignStatus.ACTIVE);

    private static final Logger log = LoggerFactory.getLogger(CampaignLifecycleService.class);

    /** Выполненный переход статуса кампании. */
    public record StatusChange(Long campaignId, String name, CampaignStatus from, CampaignStatus to) {
    }

    private final CampaignRepository campaignRepository;
    private final AuditTrail auditTrail;
    private final Clock clock;

    public CampaignLifecycleService(CampaignRepository campaignRepository, AuditTrail auditTrail, Clock clock) {
        this.campaignRepository = campaignRepository;
        this.auditTrail = auditTrail;
        this.clock = clock;
    }

    /** Синхронизация на сегодняшнюю бизнес-дату (часы приложения, часовой пояс {@code bonus.zone}). */
    @Transactional
    public List<StatusChange> synchronizeToday() {
        return synchronize(LocalDate.now(clock));
    }

    /**
     * Приводит статусы кампаний в соответствие с датой {@code today}.
     *
     * @return выполненные переходы (пустой список — менять нечего)
     */
    @Transactional
    public List<StatusChange> synchronize(LocalDate today) {
        Objects.requireNonNull(today, "today");
        List<StatusChange> changes = new ArrayList<>();
        for (Campaign campaign : campaignRepository.findStartedInStatuses(DATE_DRIVEN, today)) {
            Optional<CampaignStatus> target = targetStatus(campaign.getStatus(), campaign.getStartDate(),
                    campaign.getEndDate(), today);
            if (target.isEmpty()) {
                continue;
            }
            CampaignStatus from = campaign.getStatus();
            CampaignStatus to = target.get();
            if (campaignRepository.updateStatus(campaign.getId(), from.dbValue(), to.dbValue()) == 0) {
                continue;   // статус уже изменён параллельной синхронизацией — запись аудита сделала она
            }
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("label", campaign.getName());
            details.put("from", from.dbValue());
            details.put("to", to.dbValue());
            details.put("startDate", campaign.getStartDate().toString());
            details.put("endDate", campaign.getEndDate().toString());
            details.put("onDate", today.toString());
            auditTrail.record(null, AUDIT_ACTION, "campaign", campaign.getId(), details);
            changes.add(new StatusChange(campaign.getId(), campaign.getName(), from, to));
        }
        if (!changes.isEmpty()) {
            log.info("Статусы кампаний на {} обновлены: {}", today, changes);
        }
        return changes;
    }

    /**
     * Новый статус кампании на дату {@code today}; пусто — статус не меняется.
     * Меняются только «Запланирована» и «Активна»; «Требуется утверждение» и «Завершена» — никогда.
     */
    static Optional<CampaignStatus> targetStatus(CampaignStatus status, LocalDate startDate, LocalDate endDate,
                                                 LocalDate today) {
        if (!DATE_DRIVEN.contains(status)) {
            return Optional.empty();
        }
        if (today.isAfter(endDate)) {
            return Optional.of(CampaignStatus.FINISHED);
        }
        if (status == CampaignStatus.PLANNED && !today.isBefore(startDate)) {
            return Optional.of(CampaignStatus.ACTIVE);
        }
        return Optional.empty();
    }
}
