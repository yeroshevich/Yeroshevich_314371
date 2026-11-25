package by.technosoft.bonus.service.event;

import by.technosoft.bonus.domain.AppUser;
import by.technosoft.bonus.domain.Operation;
import by.technosoft.bonus.domain.OperationStatus;

/**
 * Доменное событие (паттерн Observer): с операцией выполнено значимое действие.
 * Неизменяемый снимок без ссылок на JPA-сущности — его безопасно обрабатывать после коммита.
 *
 * @param action         что произошло
 * @param operationId    операция
 * @param operationNumber номер «ОП-000123»
 * @param employeeId     владелец счёта
 * @param actorUserId    инициатор; {@code null} — система
 * @param amount         сумма операции
 * @param originalAmount сумма до корректировки (UC7)
 * @param status         статус после действия
 * @param reason         причина операции
 * @param comment        комментарий (решение финотдела, превышение лимита и т. п.)
 */
public record OperationRegisteredEvent(
        OperationAction action,
        Long operationId,
        String operationNumber,
        Long employeeId,
        Long actorUserId,
        int amount,
        Integer originalAmount,
        OperationStatus status,
        String reason,
        String comment) {

    /** Снимок события по операции; вызывать внутри транзакции. */
    public static OperationRegisteredEvent of(OperationAction action, Operation op, AppUser actor, String comment) {
        return new OperationRegisteredEvent(
                action,
                op.getId(),
                op.getNumber(),
                op.getAccount().getEmployee().getId(),
                actor == null ? null : actor.getId(),
                op.getAmount(),
                op.getOriginalAmount(),
                op.getStatus(),
                op.getReason(),
                comment);
    }
}
