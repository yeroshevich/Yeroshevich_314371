package by.technosoft.bonus.web.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Запрос предварительного расчёта (UC6). Те же поля, что у {@link AccrualRequestDto}, но причина
 * необязательна: клиент пересчитывает итог «на лету», пока пользователь ещё не ввёл причину,
 * а расчёт от причины не зависит и ничего не сохраняет.
 */
@Schema(name = "AccrualPreviewRequest")
public record AccrualPreviewRequestDto(
        @Schema(example = "[\"1\", \"3\"]")
        @NotEmpty(message = "Выберите хотя бы одного сотрудника.")
        @Size(max = 500, message = "За один раз — не больше 500 сотрудников.")
        List<@NotNull Long> employeeIds,
        @Schema(example = "800")
        @Positive(message = "Укажите сумму больше нуля.") @Max(value = 1_000_000, message = "Сумма слишком велика.")
        int amount,
        @Schema(allowableValues = {"kpi", "project", "achievement"})
        @NotNull(message = "Выберите основание начисления.")
        @Pattern(regexp = "kpi|project|achievement", message = "Основание: kpi, project или achievement.")
        String basis,
        @Schema(description = "Необязательна для расчёта")
        @Size(max = 300, message = "Причина — не длиннее 300 символов.") String reason,
        Long ruleId,
        Long campaignId) {
}
