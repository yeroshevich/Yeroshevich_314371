package by.technosoft.bonus.service.event;

/**
 * Доменное событие: бизнес-событие не удалось обработать и оно отложено (альт. поток UC4).
 *
 * @param eventId    бизнес-событие
 * @param eventType  тип события
 * @param employeeId сотрудник
 * @param message    причина
 */
public record EventProcessingFailedEvent(Long eventId, String eventType, Long employeeId, String message) {
}
