package by.technosoft.bonus.web.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Ручное начисление и его предварительный расчёт (UC5, UC6; TS: {@code AccrualRequest}). */
@Schema(name = "AccrualRequest")
public record AccrualRequestDto(
        @Schema(description = "Сотрудники-получатели (id строкой или числом)", example = "[\"1\", \"3\"]")
        @NotEmpty(message = "Выберите хотя бы одного сотрудника.")
        @Size(max = 500, message = "За один раз — не больше 500 сотрудников.")
        List<@NotNull Long> employeeIds,
        @Schema(description = "Сумма на одного сотрудника до коэффициента кампании", example = "800")
        @Positive(message = "Укажите сумму больше нуля.") @Max(value = 1_000_000, message = "Сумма слишком велика.")
        int amount,
        @Schema(allowableValues = {"kpi", "project", "achievement"})
        @NotNull(message = "Выберите основание начисления.")
        @Pattern(regexp = "kpi|project|achievement", message = "Основание: kpi, project или achievement.")
        String basis,
        @Schema(example = "Срочный релиз платформы 2.0 в выходные")
        @NotBlank(message = "Укажите причину начисления.") @Size(max = 300, message = "Причина — не длиннее 300 символов.")
        String reason,
        Long ruleId,
        Long campaignId) {
}
