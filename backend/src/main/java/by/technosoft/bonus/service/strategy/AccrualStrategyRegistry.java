package by.technosoft.bonus.service.strategy;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import by.technosoft.bonus.domain.AccrualRule;
import by.technosoft.bonus.domain.RulePeriodicity;

/**
 * Реестр (фабрика) стратегий: сопоставляет периодичность правила и стратегию расчёта.
 * Spring передаёт в конструктор все бины {@link AccrualStrategy}; при старте проверяется,
 * что каждая периодичность обслуживается ровно одной стратегией.
 */
@Component
public class AccrualStrategyRegistry {

    private final Map<RulePeriodicity, AccrualStrategy> byPeriodicity;

    public AccrualStrategyRegistry(List<AccrualStrategy> strategies) {
        Map<RulePeriodicity, AccrualStrategy> map = new EnumMap<>(RulePeriodicity.class);
        for (AccrualStrategy strategy : strategies) {
            for (RulePeriodicity periodicity : strategy.supportedPeriodicities()) {
                AccrualStrategy previous = map.putIfAbsent(periodicity, strategy);
                if (previous != null) {
                    throw new IllegalStateException("Периодичность " + periodicity + " обслуживают две стратегии: "
                            + previous.getClass().getSimpleName() + " и " + strategy.getClass().getSimpleName());
                }
            }
        }
        for (RulePeriodicity periodicity : RulePeriodicity.values()) {
            if (!map.containsKey(periodicity)) {
                throw new IllegalStateException("Нет стратегии расчёта для периодичности " + periodicity);
            }
        }
        this.byPeriodicity = Collections.unmodifiableMap(map);
    }

    /** Стратегия для правила. */
    public AccrualStrategy forRule(AccrualRule rule) {
        return byPeriodicity.get(rule.getPeriodicity());
    }
}
