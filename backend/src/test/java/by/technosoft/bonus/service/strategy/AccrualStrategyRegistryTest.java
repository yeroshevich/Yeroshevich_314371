package by.technosoft.bonus.service.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import by.technosoft.bonus.domain.AccrualRule;
import by.technosoft.bonus.domain.RulePeriodicity;
import by.technosoft.bonus.domain.TestEntities;

/** Реестр стратегий: выбор способа расчёта по периодичности правила. */
@DisplayName("AccrualStrategyRegistry — выбор стратегии по правилу")
class AccrualStrategyRegistryTest {

    private final KpiAccrualStrategy kpi = new KpiAccrualStrategy();
    private final FixedEventAccrualStrategy fixed = new FixedEventAccrualStrategy();
    private final AccrualStrategyRegistry registry = new AccrualStrategyRegistry(List.of(kpi, fixed));

    private static AccrualRule ruleWith(RulePeriodicity periodicity) {
        return TestEntities.rule(1, "Правило", TestEntities.kpi(1, "K", "K"), "0.50", 1000, periodicity, "0",
                "{}", true, LocalDate.of(2026, 1, 1));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(RulePeriodicity.class)
    @DisplayName("FR8-3: для каждой периодичности правила есть ровно одна стратегия расчёта")
    void everyPeriodicityHasStrategy(RulePeriodicity periodicity) {
        AccrualStrategy strategy = registry.forRule(ruleWith(periodicity));
        assertThat(strategy).isNotNull();
        assertThat(strategy.supportedPeriodicities()).contains(periodicity);
    }

    @Test
    @DisplayName("FR8-3: правила по KPI считаются стратегией KPI, по событию и ежегодные — фиксированной")
    void picksStrategyByPeriodicity() {
        assertThat(registry.forRule(ruleWith(RulePeriodicity.MONTH))).isSameAs(kpi);
        assertThat(registry.forRule(ruleWith(RulePeriodicity.QUARTER))).isSameAs(kpi);
        assertThat(registry.forRule(ruleWith(RulePeriodicity.EVENT))).isSameAs(fixed);
        assertThat(registry.forRule(ruleWith(RulePeriodicity.YEAR))).isSameAs(fixed);
    }

    @Test
    @DisplayName("FR8-3: при запуске обнаруживается периодичность без стратегии расчёта")
    void missingStrategyFailsFast() {
        assertThatThrownBy(() -> new AccrualStrategyRegistry(List.of(kpi)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Нет стратегии расчёта");
    }

    @Test
    @DisplayName("FR8-3: две стратегии на одну периодичность — ошибка конфигурации")
    void duplicateStrategyFailsFast() {
        AccrualStrategy another = new AccrualStrategy() {
            @Override
            public Set<RulePeriodicity> supportedPeriodicities() {
                return EnumSet.of(RulePeriodicity.MONTH);
            }

            @Override
            public BaseAmount calculate(AccrualRule rule, BigDecimal kpiValue) {
                return new BaseAmount(BigDecimal.ONE, "1");
            }
        };
        assertThatThrownBy(() -> new AccrualStrategyRegistry(List.of(kpi, fixed, another)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("обслуживают две стратегии");
    }
}
