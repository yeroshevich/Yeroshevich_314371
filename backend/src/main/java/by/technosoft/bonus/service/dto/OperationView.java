package by.technosoft.bonus.service.dto;

import java.time.OffsetDateTime;

import by.technosoft.bonus.domain.Employee;
import by.technosoft.bonus.domain.Operation;
import by.technosoft.bonus.domain.OperationBasis;
import by.technosoft.bonus.domain.OperationStatus;
import by.technosoft.bonus.domain.OperationType;

/**
 * Снимок операции для вызывающего слоя (без ленивых связей JPA).
 * Поля соответствуют DTO {@code Operation} клиента.
 */
public record OperationView(
        Long id,
        String number,
        Long employeeId,
        String employeeName,
        OperationType type,
        OperationStatus status,
        OperationBasis basis,
        int amount,
        Integer originalAmount,
        String reason,
        Long ruleId,
        Long campaignId,
        Long programId,
        Long correctsId,
        Long createdBy,
        OffsetDateTime createdAt) {

    /** Создаёт снимок; вызывать внутри транзакции. */
    public static OperationView of(Operation op) {
        Employee employee = op.getAccount().getEmployee();
        return new OperationView(
                op.getId(),
                op.getNumber(),
                employee.getId(),
                employee.getFullName(),
                op.getType(),
                op.getStatus(),
                op.getBasis(),
                op.getAmount(),
                op.getOriginalAmount(),
                op.getReason(),
                op.getRule() == null ? null : op.getRule().getId(),
                op.getCampaign() == null ? null : op.getCampaign().getId(),
                op.getProgram() == null ? null : op.getProgram().getId(),
                op.getCorrects() == null ? null : op.getCorrects().getId(),
                op.getCreatedBy() == null ? null : op.getCreatedBy().getId(),
                op.getCreatedAt());
    }
}
