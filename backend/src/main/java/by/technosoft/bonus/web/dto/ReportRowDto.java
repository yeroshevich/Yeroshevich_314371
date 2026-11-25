package by.technosoft.bonus.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Строка отчёта (UC10, TS: {@code ReportRow}). */
@Schema(name = "ReportRow")
public record ReportRowDto(
        String key,
        String name,
        String group,
        int accrued,
        int spent,
        int pending,
        @Schema(description = "Баланс (сотрудник, подразделение) или остаток бюджета (кампания)") Integer balance,
        int operations) {
}
