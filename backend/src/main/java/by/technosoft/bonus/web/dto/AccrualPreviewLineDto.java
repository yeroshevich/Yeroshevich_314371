package by.technosoft.bonus.web.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

/** Строка предварительного расчёта (TS: {@code AccrualPreviewLine}). */
@Schema(name = "AccrualPreviewLine")
public record AccrualPreviewLineDto(
        String employeeId,
        String employeeName,
        int baseAmount,
        @Schema(example = "1.5") BigDecimal coefficient,
        int total,
        @Schema(description = "Итог выше лимита — уйдёт на согласование финотдела") boolean needsApproval) {
}
