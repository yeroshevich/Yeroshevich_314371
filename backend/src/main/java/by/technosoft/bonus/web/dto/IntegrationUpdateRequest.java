package by.technosoft.bonus.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Частичное изменение интеграции (UC13): переданные поля меняются, остальные — нет. */
public record IntegrationUpdateRequest(
        @Schema(example = "https://hris.technosoft.by/api/v3")
        @Size(max = 255, message = "Адрес — не длиннее 255 символов.")
        @Pattern(regexp = "^https?://\\S+$", message = "Адрес должен начинаться с https://") String url,
        @Schema(description = "Новый ключ API; маска или пусто — оставить сохранённый")
        @Size(max = 200, message = "Ключ — не длиннее 200 символов.") String apiKey,
        @Schema(description = "Включить или выключить синхронизацию") Boolean enabled) {
}
