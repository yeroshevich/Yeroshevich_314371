package by.technosoft.bonus.web.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** Сводка «Мой баланс» (UC1, TS: {@code AccountSummary}). */
@Schema(name = "AccountSummary")
public record AccountSummaryDto(
        BonusAccountDto account,
        int accruedThisMonth,
        int spentThisMonth,
        @Schema(description = "Последние 5 операций") List<OperationDto> recent,
        @Schema(description = "Баланс на конец каждого из 6 последних месяцев") List<MonthBalanceDto> history) {
}
