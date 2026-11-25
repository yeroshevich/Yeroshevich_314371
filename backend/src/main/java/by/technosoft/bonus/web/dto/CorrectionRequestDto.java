package by.technosoft.bonus.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Корректировка операции (UC7, TS: {@code CorrectionRequest}). */
@Schema(name = "CorrectionRequest")
public record CorrectionRequestDto(
        @Schema(allowableValues = {"adjust", "cancel"})
        @NotNull(message = "Выберите действие: изменить сумму или отменить.")
        @Pattern(regexp = "adjust|cancel", message = "Действие: adjust или cancel.") String action,
        @Schema(description = "Новая сумма (для adjust)", example = "600")
        @Positive(message = "Сумма должна быть больше нуля. Для отмены выберите «Отменить начисление».")
        @Max(value = 1_000_000, message = "Сумма слишком велика.") Integer newAmount,
        @NotBlank(message = "Укажите причину корректировки.")
        @Size(max = 300, message = "Причина — не длиннее 300 символов.") String reason) {
}
