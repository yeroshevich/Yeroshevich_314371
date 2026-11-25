package by.technosoft.bonus.web.dto;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

/** Сотрудник (TS: {@code Employee}). */
@Schema(name = "Employee")
public record EmployeeDto(
        String id,
        String fullName,
        String email,
        String position,
        String departmentId,
        String managerId,
        LocalDate hiredAt,
        @Schema(description = "Выполнение KPI за текущий период, % (0–150)") int kpiCompletion) {
}
