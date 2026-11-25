package by.technosoft.bonus.web.dto;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/** Подключение к внешней системе (UC13, TS: {@code Integration}). */
@Schema(name = "Integration")
public record IntegrationDto(
        String id,
        @Schema(allowableValues = {"hris", "erp", "ad", "notifications"}) String kind,
        String name,
        String description,
        String url,
        @Schema(description = "Маска ключа: сам ключ хранится зашифрованным и не возвращается (DATA-4)",
                example = "••••••••") String apiKey,
        boolean enabled,
        OffsetDateTime lastSyncAt,
        IntegrationCheckDto lastCheck) {
}
