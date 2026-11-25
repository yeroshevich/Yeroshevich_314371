package by.technosoft.bonus.web.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** Дашборд эффективности (UC11, TS: {@code AnalyticsDashboard}). */
@Schema(name = "AnalyticsDashboard")
public record AnalyticsDashboardDto(
        @Schema(allowableValues = {"month", "quarter", "halfyear"}) String period,
        @Schema(example = "сен – ноя 2026") String currentLabel,
        @Schema(example = "июн – авг 2026") String previousLabel,
        Comparison averageBonus,
        @Schema(description = "Доля сотрудников, потративших баллы на программы, %") Comparison programConversion,
        Comparison totalAccrued,
        Comparison totalSpent,
        List<DepartmentComparison> byDepartment,
        List<MonthlyFlow> monthly,
        @Schema(description = "Данных меньше, чем нужно для полного сравнения (альт. поток UC11)") boolean partial) {

    public record Comparison(int current, int previous) {
    }

    public record DepartmentComparison(String department, int current, int previous) {
    }

    public record MonthlyFlow(@Schema(example = "ноя") String month, int accrued, int spent) {
    }
}
