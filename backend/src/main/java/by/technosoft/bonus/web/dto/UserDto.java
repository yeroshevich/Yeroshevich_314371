package by.technosoft.bonus.web.dto;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/** Учётная запись (TS: {@code User}). */
@Schema(name = "User")
public record UserDto(
        @Schema(example = "1") String id,
        @Schema(example = "Анна Ковалёва") String fullName,
        @Schema(example = "anna.kovaleva@technosoft.by") String email,
        @Schema(example = "1") String departmentId,
        @Schema(example = "Frontend-разработчик") String position,
        @Schema(allowableValues = {"employee", "manager", "hr", "finance", "admin"}) String role,
        @Schema(allowableValues = {"active", "blocked"}) String status,
        @Schema(description = "Сотрудник с бонусным счётом", example = "1") String employeeId,
        OffsetDateTime lastLoginAt) {
}
