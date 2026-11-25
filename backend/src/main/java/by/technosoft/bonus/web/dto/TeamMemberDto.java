package by.technosoft.bonus.web.dto;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

/** Строка «Моя команда» (TS: {@code TeamMember extends Employee}). */
@Schema(name = "TeamMember")
public record TeamMemberDto(
        String id,
        String fullName,
        String email,
        String position,
        String departmentId,
        String managerId,
        LocalDate hiredAt,
        int kpiCompletion,
        String departmentName,
        int balance,
        @Schema(allowableValues = {"active", "blocked"}) String accountStatus,
        int accruedThisMonth) {
}
