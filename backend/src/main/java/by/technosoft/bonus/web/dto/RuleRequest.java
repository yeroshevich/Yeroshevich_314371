package by.technosoft.bonus.web.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Создание и изменение правила (UC8, TS: {@code RuleInput}). */
@Schema(name = "RuleInput")
public record RuleRequest(
        @Schema(example = "Выполнение плана спринта")
        @NotBlank(message = "Укажите название правила.") @Size(max = 150, message = "Название — не длиннее 150 символов.")
        String name,
        @Schema(description = "Название показателя KPI; новый показатель добавляется в справочник",
                example = "Выполнение плана спринта")
        @NotBlank(message = "Укажите показатель KPI.") @Size(max = 150) String kpi,
        @Schema(example = "0.4")
        @NotNull(message = "Укажите вес показателя.")
        @DecimalMin(value = "0.05", message = "Вес показателя — от 0,05 до 1.")
        @DecimalMax(value = "1", message = "Вес показателя — от 0,05 до 1.")
        @Digits(integer = 1, fraction = 2, message = "Вес — не больше двух знаков после запятой.") BigDecimal weight,
        @Schema(example = "3000")
        @Positive(message = "Базовая сумма должна быть больше нуля.") @Max(value = 1_000_000) int basePoints,
        @Schema(allowableValues = {"month", "quarter", "year", "event"})
        @NotNull(message = "Выберите периодичность.")
        @Pattern(regexp = "month|quarter|year|event", message = "Периодичность: month, quarter, year или event.")
        String periodicity,
        @Schema(example = "80")
        @NotNull(message = "Укажите порог срабатывания.")
        @DecimalMin(value = "0", message = "Порог — от 0 до 150 %.") @DecimalMax(value = "150", message = "Порог — от 0 до 150 %.")
        @Digits(integer = 3, fraction = 2) BigDecimal threshold,
        boolean active) {
}
