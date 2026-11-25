package by.technosoft.bonus.service.dto;

/**
 * Результат использования баллов на программу мотивации (UC3).
 *
 * @param operation операция списания
 * @param balance   баланс счёта после списания
 */
public record RedemptionResult(OperationView operation, int balance) {
}
