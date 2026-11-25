package by.technosoft.bonus.service.dto;

import by.technosoft.bonus.domain.Operation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Запрос корректировки операции (UC7).
 *
 * @param action    изменить сумму или отменить
 * @param newAmount новая сумма (только для {@link CorrectionAction#ADJUST})
 * @param reason    причина корректировки
 */
public record CorrectionRequest(
        @NotNull CorrectionAction action,
        @Positive Integer newAmount,
        @NotBlank @Size(max = Operation.REASON_LENGTH) String reason) {

    public static CorrectionRequest adjust(int newAmount, String reason) {
        return new CorrectionRequest(CorrectionAction.ADJUST, newAmount, reason);
    }

    public static CorrectionRequest cancel(String reason) {
        return new CorrectionRequest(CorrectionAction.CANCEL, null, reason);
    }
}
