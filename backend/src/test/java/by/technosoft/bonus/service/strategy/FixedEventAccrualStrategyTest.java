package by.technosoft.bonus.service.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import by.technosoft.bonus.domain.AccrualRule;
import by.technosoft.bonus.domain.RulePeriodicity;
import by.technosoft.bonus.domain.TestEntities;

/** Стратегия правил «по событию» и ежегодных: факт события — подтверждённый результат. */
@DisplayName("FixedEventAccrualStrategy — расчёт по событию")
class FixedEventAccrualStrategyTest {

    private final FixedEventAccrualStrategy strategy = new FixedEventAccrualStrategy();

    private static AccrualRule stage() {
        return TestEntities.rule(3, "Сдача этапа проекта", TestEntities.kpi(3, "STAGE", "Этап принят"), "0.50", 6000,
                RulePeriodicity.EVENT, "100.00", "{\"roundTo\": 50}", true, LocalDate.of(2026, 6, 1));
    }

    @Test
    @DisplayName("FR6-2: сумма по событию = базовые баллы × вес (6000 × 0,5 = 3000)")
    void basePointsTimesWeight() {
        BaseAmount base = strategy.calculate(stage(), null);

        assertThat(base.value()).isEqualByComparingTo("3000");
        assertThat(base.formula()).isEqualTo("6000 × 0.50 = 3000");
    }

    @Test
    @DisplayName("FR6-2: значение KPI для правила по событию не влияет на сумму")
    void kpiValueIsIgnored() {
        assertThat(strategy.calculate(stage(), new BigDecimal("10")).value()).isEqualByComparingTo("3000");
    }

    @Test
    @DisplayName("FR8-3: стратегия обслуживает правила «по событию» и ежегодные")
    void supportsEventAndYear() {
        assertThat(strategy.supportedPeriodicities())
                .containsExactlyInAnyOrder(RulePeriodicity.EVENT, RulePeriodicity.YEAR);
    }
}
