package by.technosoft.bonus.service.strategy;

import java.math.BigDecimal;
import java.util.Set;

import by.technosoft.bonus.domain.AccrualRule;
import by.technosoft.bonus.domain.RulePeriodicity;

/**
 * Паттерн Strategy: способ расчёта базовой суммы бонуса по правилу (UC6, BR-4).
 * Реализация выбирается {@link AccrualStrategyRegistry} по периодичности правила;
 * новый способ расчёта добавляется новым классом без изменения сервиса расчёта.
 */
public interface AccrualStrategy {

    /** Периодичности правил, которые обслуживает стратегия. */
    Set<RulePeriodicity> supportedPeriodicities();

    /**
     * Рассчитывает сумму до коэффициента кампании.
     *
     * @param rule     правило начисления
     * @param kpiValue уровень достижения KPI, % (может быть {@code null}, если стратегии он не нужен)
     * @throws by.technosoft.bonus.service.exception.InvalidCalculationInputException некорректные входные данные
     */
    BaseAmount calculate(AccrualRule rule, BigDecimal kpiValue);
}
