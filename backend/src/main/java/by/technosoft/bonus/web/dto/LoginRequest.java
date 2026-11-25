package by.technosoft.bonus.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Вход по email и паролю (SEC-1). */
public record LoginRequest(
        @Schema(example = "anna.kovaleva@technosoft.by")
        @NotBlank(message = "Введите email.") @Email(message = "Похоже, в адресе опечатка: нужен вид name@technosoft.by.")
        @Size(max = 150) String email,
        @Schema(example = "demo")
        @NotBlank(message = "Введите пароль.") @Size(max = 100) String password) {
}
