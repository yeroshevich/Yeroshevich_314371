package by.technosoft.bonus.service.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import by.technosoft.bonus.domain.AccrualRule;
import by.technosoft.bonus.domain.RulePeriodicity;
import by.technosoft.bonus.domain.TestEntities;
import by.technosoft.bonus.service.exception.InvalidCalculationInputException;

/** Стратегия расчёта по KPI (месяц, квартал): BR-4 «вес × уровень достижения», BR-5 «ниже порога — 0». */
@DisplayName("KpiAccrualStrategy — расчёт базы по KPI")
class KpiAccrualStrategyTest {

    private final KpiAccrualStrategy strategy = new KpiAccrualStrategy();
    private final AccrualRule sprint = TestEntities.sprintRule(); // 3000 × 0,40, порог 80 %

    @Test
    @DisplayName("FR6-2: база = базовые баллы × вес × KPI / 100 (3000 × 0,4 × 116 % = 1392)")
    void appliesWeightAndKpiLevel() {
        BaseAmount base = strategy.calculate(sprint, new BigDecimal("116"));

        assertThat(base.value()).isEqualByComparingTo("1392");
        assertThat(base.isZero()).isFalse();
        assertThat(base.formula()).isEqualTo("3000 × 0.40 × 116 % = 1392");
    }

    @ParameterizedTest(name = "KPI {0} % → {1}")
    @CsvSource({"80, 960", "100, 1200", "150, 1800", "91, 1092", "99.99, 1199.88"})
    @DisplayName("FR6-2: KPI на пороге и выше порога даёт бонус пропорционально уровню достижения")
    void proportionalToKpiFromThreshold(String kpi, String expected) {
        assertThat(strategy.calculate(sprint, new BigDecimal(kpi)).value()).isEqualByComparingTo(expected);
    }

    @Test
    @DisplayName("FR6-2: KPI ниже порога правила — бонус не положен, база 0 (BR-5)")
    void belowThresholdGivesZero() {
        BaseAmount base = strategy.calculate(sprint, new BigDecimal("79.99"));

        assertThat(base.isZero()).isTrue();
        assertThat(base.formula()).contains("ниже порога 80 %").contains("BR-5");
    }

    @Test
    @DisplayName("FR6-1: без значения KPI расчёт не выполняется — некорректные входные данные")
    void missingKpiIsRejected() {
        assertThatThrownBy(() -> strategy.calculate(sprint, null))
                .isInstanceOf(InvalidCalculationInputException.class)
                .hasMessageContaining("значение KPI отсутствует");
    }

    @ParameterizedTest(name = "KPI {0} %")
    @CsvSource({"-1", "150.01", "1000"})
    @DisplayName("FR6-1: значение KPI вне диапазона 0–150 % отклоняется")
    void outOfRangeKpiIsRejected(String kpi) {
        assertThatThrownBy(() -> strategy.calculate(sprint, new BigDecimal(kpi)))
                .isInstanceOf(InvalidCalculationInputException.class)
                .hasMessageContaining("вне диапазона 0–150 %");
    }

    @Test
    @DisplayName("FR8-3: стратегия обслуживает периодические правила по KPI — месяц и квартал")
    void supportsMonthAndQuarter() {
        assertThat(strategy.supportedPeriodicities())
                .containsExactlyInAnyOrder(RulePeriodicity.MONTH, RulePeriodicity.QUARTER);
    }
}
