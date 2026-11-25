package by.technosoft.bonus.service.dto;

import java.math.BigDecimal;

/**
 * Результат расчёта бонуса (UC6) — объект-значение.
 *
 * @param employeeId  сотрудник
 * @param ruleId      правило ({@code null} для ручной суммы)
 * @param baseAmount  сумма до коэффициента кампании
 * @param coefficient применённый коэффициент (1 — без кампании)
 * @param campaignId  применённая кампания или {@code null}
 * @param total       итог после коэффициента и округления; 0 — бонус не положен (BR-5)
 * @param formula     расшифровка расчёта для пользователя и аудита
 */
public record CalculationResult(
        Long employeeId,
        Long ruleId,
        BigDecimal baseAmount,
        BigDecimal coefficient,
        Long campaignId,
        int total,
        String formula) {

    /** Бонус не положен: начисление не создаётся. */
    public boolean isZero() {
        return total == 0;
    }
}
