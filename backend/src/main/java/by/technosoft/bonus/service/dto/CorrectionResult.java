package by.technosoft.bonus.service.dto;

/**
 * Результат корректировки (UC7).
 *
 * @param original        корректируемая операция (после проведения — cancelled)
 * @param correction      новая операция типа correction
 * @param pendingApproval корректировка ушла на согласование: баланс стал бы отрицательным
 */
public record CorrectionResult(OperationView original, OperationView correction, boolean pendingApproval) {
}
