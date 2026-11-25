package by.technosoft.bonus.web.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** Отчёт за период (UC10, TS: {@code Report}). */
@Schema(name = "Report")
public record ReportDto(
        @Schema(allowableValues = {"employees", "departments", "campaigns"}) String type,
        LocalDate from,
        LocalDate to,
        List<ReportRowDto> rows,
        OffsetDateTime generatedAt) {
}
