package by.technosoft.bonus.web.dto;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/** Запись журнала аудита (NFR-3, SEC-4; TS: {@code AuditEntry}). */
@Schema(name = "AuditEntry")
public record AuditEntryDto(
        String id,
        OffsetDateTime timestamp,
        @Schema(example = "Ольга Мельник") String userName,
        @Schema(allowableValues = {"employee", "manager", "hr", "finance", "admin", "system"}) String role,
        @Schema(example = "Ручное начисление") String action,
        @Schema(example = "Операция") String objectType,
        @Schema(example = "ОП-000154, 800 Б") String objectLabel,
        @Schema(example = "127.0.0.1") String ip) {
}
