package by.technosoft.bonus.service.dto;

import java.math.BigDecimal;

/**
 * Строка предварительного расчёта ручного начисления (UC5/UC6).
 *
 * @param employeeId    сотрудник
 * @param employeeName  ФИО
 * @param baseAmount    сумма до коэффициента
 * @param coefficient   применённый коэффициент кампании (1 — без кампании)
 * @param campaignId    применённая кампания или {@code null}
 * @param total         итоговая сумма
 * @param needsApproval итог превышает лимит — операция уйдёт на согласование финотдела
 */
public record AccrualPreviewLine(
        Long employeeId,
        String employeeName,
        int baseAmount,
        BigDecimal coefficient,
        Long campaignId,
        int total,
        boolean needsApproval) {
}
