package by.technosoft.bonus.service.dto;

import java.util.List;

import by.technosoft.bonus.domain.Operation;
import by.technosoft.bonus.domain.OperationBasis;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Запрос ручного начисления (UC5).
 *
 * @param employeeIds сотрудники-получатели
 * @param amount      сумма на одного сотрудника до коэффициента кампании, баллы
 * @param basis       основание: kpi, project или achievement
 * @param reason      причина (видна сотруднику)
 * @param ruleId      правило, на которое ссылается начисление (необязательно)
 * @param campaignId  кампания, коэффициент которой нужно применить (необязательно)
 */
public record AccrualRequest(
        @NotEmpty List<@NotNull Long> employeeIds,
        @Positive int amount,
        @NotNull OperationBasis basis,
        @NotBlank @Size(max = Operation.REASON_LENGTH) String reason,
        Long ruleId,
        Long campaignId) {
}
