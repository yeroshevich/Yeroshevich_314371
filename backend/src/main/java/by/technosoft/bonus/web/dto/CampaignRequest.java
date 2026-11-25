package by.technosoft.bonus.web.dto;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Новая кампания (UC9, TS: {@code CampaignInput}). */
@Schema(name = "CampaignInput")
public record CampaignRequest(
        @Schema(example = "Зимний спринт качества")
        @NotBlank(message = "Укажите название кампании.") @Size(max = 150, message = "Название — не длиннее 150 символов.")
        String name,
        @Size(max = 2000, message = "Описание — не длиннее 2000 символов.") String description,
        @Schema(description = "Дата начала: YYYY-MM-DD или момент ISO 8601 (переводится в дату по Europe/Minsk)",
                example = "2027-01-10")
        @NotBlank(message = "Укажите дату начала.") String startDate,
        @Schema(example = "2027-01-31")
        @NotBlank(message = "Укажите дату окончания.") String endDate,
        @Schema(example = "[\"1\", \"2\"]")
        @NotEmpty(message = "Выберите хотя бы одно целевое подразделение.") List<@NotNull Long> departmentIds,
        @Schema(example = "1.5")
        @NotNull(message = "Укажите коэффициент.")
        @DecimalMin(value = "1", message = "Коэффициент — от 1 до 5.") @DecimalMax(value = "5", message = "Коэффициент — от 1 до 5.")
        @Digits(integer = 1, fraction = 2, message = "Коэффициент — не больше двух знаков после запятой.")
        BigDecimal coefficient,
        @Schema(description = "Бюджет, баллы; null — не выделен (статус needs_approval)", nullable = true)
        @Positive(message = "Бюджет должен быть больше нуля или не задан.") Integer budget) {
}
