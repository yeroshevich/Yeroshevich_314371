package by.technosoft.bonus.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Параметры проверки соединения (UC13). */
public record IntegrationTestRequest(
        @Schema(example = "https://hris.technosoft.by/api/v2")
        @NotBlank(message = "Укажите адрес.") @Size(max = 255, message = "Адрес — не длиннее 255 символов.") String url,
        @Schema(description = "Ключ API; маска или пусто — проверить сохранённый ключ", example = "hris-7f3a9c21e4b8")
        @Size(max = 200, message = "Ключ — не длиннее 200 символов.") String apiKey) {
}
