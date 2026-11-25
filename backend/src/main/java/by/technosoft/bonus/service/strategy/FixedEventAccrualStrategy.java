package by.technosoft.bonus.service.strategy;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Set;

import org.springframework.stereotype.Component;

import by.technosoft.bonus.domain.AccrualRule;
import by.technosoft.bonus.domain.RulePeriodicity;

/**
 * Правила «по событию» и ежегодные (годовщина работы, сданный этап, сертификат):
 * сам факт события — подтверждённый результат, сумма = база × вес.
 */
@Component
public class FixedEventAccrualStrategy implements AccrualStrategy {

    @Override
    public Set<RulePeriodicity> supportedPeriodicities() {
        return EnumSet.of(RulePeriodicity.EVENT, RulePeriodicity.YEAR);
    }

    @Override
    public BaseAmount calculate(AccrualRule rule, BigDecimal kpiValue) {
        BigDecimal value = BigDecimal.valueOf(rule.getBasePoints()).multiply(rule.getWeight());
        String formula = rule.getBasePoints() + " × " + rule.getWeight().toPlainString() + " = "
                + value.stripTrailingZeros().toPlainString();
        return new BaseAmount(value, formula);
    }
}
