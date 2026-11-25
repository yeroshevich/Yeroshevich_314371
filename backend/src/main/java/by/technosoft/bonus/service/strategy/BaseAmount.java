package by.technosoft.bonus.service.strategy;

import java.math.BigDecimal;

/**
 * Сумма по правилу до коэффициента кампании и округления.
 *
 * @param value   сумма, баллы (может быть дробной)
 * @param formula расшифровка расчёта
 */
public record BaseAmount(BigDecimal value, String formula) {

    public boolean isZero() {
        return value.signum() == 0;
    }
}
