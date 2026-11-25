package by.technosoft.bonus.web.dto;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/** Бонусный счёт (TS: {@code BonusAccount}). */
@Schema(name = "BonusAccount")
public record BonusAccountDto(
        String employeeId,
        @Schema(allowableValues = {"active", "blocked"}) String status,
        @Schema(description = "Доступный баланс, баллы", example = "10600") int balance,
        @Schema(description = "Начисления на согласовании, баллы", example = "1500") int pending,
        OffsetDateTime updatedAt) {
}
