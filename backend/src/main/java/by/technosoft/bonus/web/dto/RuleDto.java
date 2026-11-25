package by.technosoft.bonus.web.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/** Правило начисления (UC8, TS: {@code Rule}). */
@Schema(name = "Rule")
public record RuleDto(
        String id,
        String name,
        @Schema(description = "Показатель KPI") String kpi,
        @Schema(example = "0.4") BigDecimal weight,
        @Schema(example = "3000") int basePoints,
        @Schema(example = "3 000 × 0,4 × уровень достижения") String formula,
        @Schema(allowableValues = {"month", "quarter", "year", "event"}) String periodicity,
        @Schema(description = "Порог — минимальное выполнение KPI, %", example = "80") BigDecimal threshold,
        boolean active,
        @Schema(description = "Правило применялось в текущем периоде: изменения будут отложены") boolean usedInCurrentPeriod,
        OffsetDateTime updatedAt) {
}
