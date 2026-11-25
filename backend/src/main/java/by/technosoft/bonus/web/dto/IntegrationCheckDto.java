package by.technosoft.bonus.web.dto;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/** Результат проверки соединения (UC13, TS: {@code IntegrationCheck}). */
@Schema(name = "IntegrationCheck")
public record IntegrationCheckDto(boolean ok, @Schema(example = "Соединение установлено") String message,
                                  OffsetDateTime checkedAt, Integer latencyMs) {
}
