package by.technosoft.bonus.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Результат списания на программу (UC3, TS: {@code Redemption}). */
@Schema(name = "Redemption")
public record RedemptionDto(OperationDto operation, BonusAccountDto account) {
}
