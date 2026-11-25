package by.technosoft.bonus.service.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;
import java.util.Set;

import org.springframework.stereotype.Component;

import by.technosoft.bonus.domain.AccrualRule;
import by.technosoft.bonus.domain.RulePeriodicity;
import by.technosoft.bonus.service.exception.InvalidCalculationInputException;

/**
 * Периодические правила по KPI (месяц, квартал).
 * BR-4: сумма = база × вес × уровень достижения / 100;
 * BR-5: уровень ниже порога правила — бонус не положен (0).
 */
@Component
public class KpiAccrualStrategy implements AccrualStrategy {

    static final BigDecimal MAX_KPI = new BigDecimal("150");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    @Override
    public Set<RulePeriodicity> supportedPeriodicities() {
        return EnumSet.of(RulePeriodicity.MONTH, RulePeriodicity.QUARTER);
    }

    @Override
    public BaseAmount calculate(AccrualRule rule, BigDecimal kpiValue) {
        if (kpiValue == null) {
            throw new InvalidCalculationInputException("Некорректные входные данные: значение KPI отсутствует");
        }
        if (kpiValue.signum() < 0 || kpiValue.compareTo(MAX_KPI) > 0) {
            throw new InvalidCalculationInputException(
                    "Некорректные входные данные: значение KPI " + kpiValue.toPlainString() + " % вне диапазона 0–150 %");
        }
        if (kpiValue.compareTo(rule.getThreshold()) < 0) {
            return new BaseAmount(BigDecimal.ZERO, "KPI " + kpiValue.toPlainString() + " % ниже порога "
                    + rule.getThreshold().stripTrailingZeros().toPlainString() + " % — бонус не положен (BR-5)");
        }
        BigDecimal value = BigDecimal.valueOf(rule.getBasePoints())
                .multiply(rule.getWeight())
                .multiply(kpiValue)
                .divide(HUNDRED, 2, RoundingMode.HALF_UP);
        String formula = rule.getBasePoints() + " × " + rule.getWeight().toPlainString() + " × "
                + kpiValue.toPlainString() + " % = " + value.stripTrailingZeros().toPlainString();
        return new BaseAmount(value, formula);
    }
}
