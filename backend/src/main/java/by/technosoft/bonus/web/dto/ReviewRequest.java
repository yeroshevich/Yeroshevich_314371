package by.technosoft.bonus.web.dto;

import jakarta.validation.constraints.Size;

/** Комментарий финотдела к подтверждению (необязателен). */
public record ReviewRequest(@Size(max = 300, message = "Комментарий — не длиннее 300 символов.") String comment) {
}
