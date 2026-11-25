package by.technosoft.bonus.web.dto;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/** Операция по счёту (TS: {@code Operation}). Необязательные поля без значения не передаются. */
@Schema(name = "Operation")
public record OperationDto(
        @Schema(example = "154") String id,
        @Schema(example = "ОП-000154") String number,
        String employeeId,
        String employeeName,
        String departmentId,
        @Schema(allowableValues = {"accrual", "writeoff", "correction"}) String type,
        @Schema(description = "Сумма со знаком: «+» начисление, «−» списание", example = "800") int amount,
        @Schema(description = "Сумма до корректировки (UC7)") Integer originalAmount,
        @Schema(allowableValues = {"accrued", "written_off", "corrected", "pending", "blocked", "cancelled"}) String status,
        @Schema(allowableValues = {"kpi", "project", "achievement", "campaign", "program", "correction"}) String basis,
        String reason,
        String ruleId,
        String campaignId,
        String programId,
        OffsetDateTime createdAt,
        @Schema(description = "ФИО автора или «Система» для автоначисления") String createdBy,
        @Schema(description = "Комментарий финотдела или причина согласования") String comment,
        @Schema(description = "Финотдел пометил операцию спорной") boolean disputed) {
}
