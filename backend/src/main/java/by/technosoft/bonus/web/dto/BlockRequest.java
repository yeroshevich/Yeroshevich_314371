package by.technosoft.bonus.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Блокировка операции финотделом: причина обязательна, её увидит сотрудник. */
public record BlockRequest(
        @Schema(example = "Нет подтверждения от руководителя проекта")
        @NotBlank(message = "Укажите причину блокировки — её увидит сотрудник.")
        @Size(max = 300, message = "Причина — не длиннее 300 символов.") String reason) {
}
