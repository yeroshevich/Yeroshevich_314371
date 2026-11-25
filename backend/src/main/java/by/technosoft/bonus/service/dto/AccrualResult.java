package by.technosoft.bonus.service.dto;

import java.util.List;

/**
 * Результат ручного начисления (UC5).
 *
 * @param operations   созданные операции
 * @param accruedCount сколько проведено сразу
 * @param pendingCount сколько ушло на согласование
 */
public record AccrualResult(List<OperationView> operations, int accruedCount, int pendingCount) {
}
