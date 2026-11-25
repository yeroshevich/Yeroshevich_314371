package by.technosoft.bonus.service.dto;

import java.util.List;

/**
 * Предварительный расчёт ручного начисления (UC5, шаг подтверждения).
 *
 * @param lines         строки по сотрудникам
 * @param total         сумма по всем строкам
 * @param limit         лимит на сотрудника
 * @param approvalCount сколько строк уйдёт на согласование
 */
public record AccrualPreview(List<AccrualPreviewLine> lines, int total, int limit, int approvalCount) {
}
