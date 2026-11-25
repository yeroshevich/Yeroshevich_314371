package by.technosoft.bonus.service.query;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import by.technosoft.bonus.domain.AccrualRule;
import by.technosoft.bonus.repository.AccrualRuleRepository;
import by.technosoft.bonus.repository.OperationRepository;
import by.technosoft.bonus.service.exception.ObjectNotFoundException;

/**
 * Чтение правил начисления (UC8). Правило — цепочка версий с одним показателем KPI;
 * на экран выводится последняя версия (в том числе ещё не вступившая в силу).
 */
@Service
@Transactional(readOnly = true)
public class RuleQueryService {

    /** Последняя версия правила и признак «применялось в текущем периоде» (изменения будут отложены). */
    public record RuleEntry(AccrualRule rule, boolean usedInCurrentPeriod) {
    }

    private static final Comparator<AccrualRule> NEWEST_VERSION =
            Comparator.comparing(AccrualRule::getEffectiveFrom).thenComparing(AccrualRule::getId);

    private final AccrualRuleRepository ruleRepository;
    private final OperationRepository operationRepository;
    private final Clock clock;

    public RuleQueryService(AccrualRuleRepository ruleRepository, OperationRepository operationRepository,
                            Clock clock) {
        this.ruleRepository = ruleRepository;
        this.operationRepository = operationRepository;
        this.clock = clock;
    }

    /** Правила (последние версии) в порядке создания. */
    public List<RuleEntry> list() {
        Map<Long, AccrualRule> latestByKpi = new LinkedHashMap<>();
        for (AccrualRule rule : ruleRepository.findAllWithKpi()) { // по возрастанию id: порядок первых версий
            latestByKpi.merge(rule.getKpi().getId(), rule,
                    (a, b) -> NEWEST_VERSION.compare(a, b) >= 0 ? a : b);
        }
        return latestByKpi.values().stream().map(this::entry).toList();
    }

    /** Версия правила с признаком использования. */
    public RuleEntry get(Long ruleId) {
        return ruleRepository.findWithKpiById(ruleId).map(this::entry)
                .orElseThrow(() -> new ObjectNotFoundException("Правило не найдено. Обновите список."));
    }

    /**
     * Применялось ли правило (любая версия с тем же показателем) в расчётах текущего периода —
     * месяца, квартала или года по периодичности правила.
     */
    public boolean usedInCurrentPeriod(AccrualRule rule) {
        LocalDate start = BonusPeriods.periodStart(rule.getPeriodicity(), LocalDate.now(clock));
        return operationRepository.existsByRuleKpiSince(rule.getKpi().getId(),
                BonusPeriods.startOfDay(start, clock.getZone()));
    }

    private RuleEntry entry(AccrualRule rule) {
        return new RuleEntry(rule, usedInCurrentPeriod(rule));
    }
}
