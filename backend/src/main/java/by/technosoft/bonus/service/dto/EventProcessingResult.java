package by.technosoft.bonus.service.dto;

/**
 * Результат обработки одного события (UC4).
 *
 * @param eventId     событие
 * @param outcome     итог
 * @param operationId созданная операция (для {@link EventOutcome#ACCRUED})
 * @param message     пояснение (формула расчёта или причина ошибки)
 */
public record EventProcessingResult(Long eventId, EventOutcome outcome, Long operationId, String message) {
}
