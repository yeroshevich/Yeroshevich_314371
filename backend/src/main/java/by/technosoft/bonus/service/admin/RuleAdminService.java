package by.technosoft.bonus.service.admin;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import by.technosoft.bonus.domain.AccrualRule;
import by.technosoft.bonus.domain.AppUser;
import by.technosoft.bonus.domain.Kpi;
import by.technosoft.bonus.domain.RoleCode;
import by.technosoft.bonus.domain.RulePeriodicity;
import by.technosoft.bonus.repository.AccrualRuleRepository;
import by.technosoft.bonus.repository.KpiRepository;
import by.technosoft.bonus.service.AccessPolicy;
import by.technosoft.bonus.service.audit.AuditTrail;
import by.technosoft.bonus.service.exception.ConflictException;
import by.technosoft.bonus.service.exception.DomainValidationException;
import by.technosoft.bonus.service.exception.ObjectNotFoundException;
import by.technosoft.bonus.service.query.BonusPeriods;
import by.technosoft.bonus.service.query.RuleQueryService;
import by.technosoft.bonus.service.query.RuleQueryService.RuleEntry;

/**
 * Настройка правил начисления HR (UC8): показатель, вес, база, периодичность, порог.
 * Правило, уже применённое в расчётах текущего периода, не меняется задним числом:
 * изменения сохраняются новой версией, которая вступает в силу с начала следующего периода
 * (альт. поток UC8). Неиспользованное правило правится на месте и действует сразу.
 */
@Service
public class RuleAdminService {

    /** Код ошибки: для показателя уже есть правило. */
    public static final String RULE_EXISTS = "RULE_EXISTS";

    /**
     * Параметры правила.
     *
     * @param kpi название показателя KPI; неизвестный показатель добавляется в справочник
     */
    public record RuleCommand(String name, String kpi, BigDecimal weight, int basePoints,
                              RulePeriodicity periodicity, BigDecimal threshold, boolean active) {
    }

    /**
     * Итог сохранения.
     *
     * @param entry         сохранённая версия правила
     * @param effectiveFrom дата вступления изменений в силу
     * @param deferred      изменения отложены до следующего периода
     */
    public record RuleSaveOutcome(RuleEntry entry, LocalDate effectiveFrom, boolean deferred) {
    }

    private final AccessPolicy accessPolicy;
    private final AccrualRuleRepository ruleRepository;
    private final KpiRepository kpiRepository;
    private final RuleQueryService ruleQueryService;
    private final AuditTrail auditTrail;
    private final Clock clock;

    public RuleAdminService(AccessPolicy accessPolicy,
                            AccrualRuleRepository ruleRepository,
                            KpiRepository kpiRepository,
                            RuleQueryService ruleQueryService,
                            AuditTrail auditTrail,
                            Clock clock) {
        this.accessPolicy = accessPolicy;
        this.ruleRepository = ruleRepository;
        this.kpiRepository = kpiRepository;
        this.ruleQueryService = ruleQueryService;
        this.auditTrail = auditTrail;
        this.clock = clock;
    }

    /** Новое правило действует сразу. Для одного показателя — одно правило (у него могут быть версии). */
    @Transactional
    public RuleSaveOutcome create(Long actorUserId, RuleCommand command) {
        requireHr(actorUserId);
        Kpi kpi = findOrCreateKpi(command.kpi());
        requireNoRuleFor(kpi, null);
        LocalDate today = LocalDate.now(clock);
        long id = ruleRepository.nextId();
        ruleRepository.insert(id, command.name().trim(), kpi.getId(), command.weight(), command.basePoints(),
                command.periodicity().dbValue(), command.threshold(), command.active(), today, now());
        auditTrail.record(actorUserId, "Создание правила", "accrual_rule", id, details(command, today, false));
        return new RuleSaveOutcome(ruleQueryService.get(id), today, false);
    }

    /** Изменение правила: на месте или новой версией со следующего периода. */
    @Transactional
    public RuleSaveOutcome update(Long actorUserId, Long ruleId, RuleCommand command) {
        requireHr(actorUserId);
        AccrualRule rule = ruleRepository.findWithKpiById(ruleId)
                .orElseThrow(() -> new ObjectNotFoundException("Правило не найдено. Обновите список."));
        AccrualRule latest = ruleRepository.findVersionsByKpiId(rule.getKpi().getId()).get(0);
        LocalDate today = LocalDate.now(clock);
        boolean future = latest.getEffectiveFrom().isAfter(today);
        boolean used = !future && ruleQueryService.usedInCurrentPeriod(latest);

        long kpiId = latest.getKpi().getId();
        if (!latest.getKpi().getName().equalsIgnoreCase(command.kpi().trim())) {
            if (future || used) {
                throw new DomainValidationException("Показатель правила, которое уже применялось в текущем периоде, "
                        + "менять нельзя. Создайте новое правило для другого показателя.");
            }
            Kpi kpi = findOrCreateKpi(command.kpi());
            requireNoRuleFor(kpi, latest.getId());
            kpiId = kpi.getId();
        }

        long targetId;
        LocalDate effectiveFrom;
        boolean deferred;
        if (used) {
            targetId = ruleRepository.nextId();
            effectiveFrom = BonusPeriods.nextPeriodStart(latest.getPeriodicity(), today);
            deferred = true;
            ruleRepository.insertVersion(targetId, latest.getId(), command.name().trim(), command.weight(),
                    command.basePoints(), command.periodicity().dbValue(), command.threshold(), command.active(),
                    effectiveFrom, now());
        } else {
            targetId = latest.getId();
            effectiveFrom = future ? latest.getEffectiveFrom() : today;
            deferred = future;
            ruleRepository.update(targetId, command.name().trim(), kpiId, command.weight(), command.basePoints(),
                    command.periodicity().dbValue(), command.threshold(), command.active(), now());
        }
        auditTrail.record(actorUserId, "Изменение правила", "accrual_rule", targetId,
                details(command, effectiveFrom, deferred));
        return new RuleSaveOutcome(ruleQueryService.get(targetId), effectiveFrom, deferred);
    }

    private void requireHr(Long actorUserId) {
        AppUser actor = accessPolicy.requireActiveUser(actorUserId);
        accessPolicy.requireRole(actor, RoleCode.HR);
    }

    private Kpi findOrCreateKpi(String name) {
        String trimmed = name.trim();
        // Сравнение в Java: upper()/lower() PostgreSQL при правилах «C» не меняют регистр кириллицы.
        return kpiRepository.findAll().stream().filter(k -> k.getName().equalsIgnoreCase(trimmed)).findFirst()
                .orElseGet(() -> {
                    long id = kpiRepository.nextId();
                    kpiRepository.insert(id, "KPI-" + id, trimmed);
                    return kpiRepository.findById(id).orElseThrow();
                });
    }

    /** Для показателя нет правил, кроме версий правила {@code exceptRuleId}. */
    private void requireNoRuleFor(Kpi kpi, Long exceptRuleId) {
        List<AccrualRule> versions = ruleRepository.findVersionsByKpiId(kpi.getId());
        if (versions.stream().anyMatch(r -> !r.getId().equals(exceptRuleId))) {
            throw new ConflictException(RULE_EXISTS, "Для показателя «" + kpi.getName() + "» уже есть правило «"
                    + versions.get(0).getName() + "». Измените его вместо создания нового.");
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    private static Map<String, Object> details(RuleCommand c, LocalDate effectiveFrom, boolean deferred) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("label", c.name().trim());
        details.put("weight", c.weight());
        details.put("basePoints", c.basePoints());
        details.put("threshold", c.threshold());
        details.put("active", c.active());
        details.put("effectiveFrom", effectiveFrom.toString());
        details.put("deferred", deferred);
        return details;
    }
}
