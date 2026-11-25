package by.technosoft.bonus.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Блокировка или разблокировка учётной записи (UC12). */
public record UserStatusRequest(
        @Schema(allowableValues = {"active", "blocked"})
        @NotNull(message = "Укажите статус.") @Pattern(regexp = "active|blocked", message = "Статус: active или blocked.")
        String status) {
}
