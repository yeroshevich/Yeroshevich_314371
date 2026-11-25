package by.technosoft.bonus.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Создание и изменение пользователя (UC12, TS: {@code UserInput}). */
@Schema(name = "UserInput")
public record UserRequest(
        @Schema(example = "Марина Захарова")
        @NotBlank(message = "Укажите ФИО.") @Size(max = 150, message = "ФИО — не длиннее 150 символов.") String fullName,
        @Schema(example = "marina.zakharova@technosoft.by")
        @NotBlank(message = "Укажите email.") @Email(message = "Похоже, в адресе опечатка: нужен вид name@technosoft.by.")
        @Size(max = 150) String email,
        @Schema(example = "2") @NotNull(message = "Выберите подразделение.") Long departmentId,
        @Schema(example = "QA-инженер")
        @NotBlank(message = "Укажите должность.") @Size(max = 100, message = "Должность — не длиннее 100 символов.")
        String position,
        @Schema(allowableValues = {"employee", "manager", "hr", "finance", "admin"})
        @NotNull(message = "Выберите роль.")
        @Pattern(regexp = "employee|manager|hr|finance|admin", message = "Роль: employee, manager, hr, finance или admin.")
        String role) {
}
