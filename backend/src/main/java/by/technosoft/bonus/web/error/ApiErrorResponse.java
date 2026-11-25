package by.technosoft.bonus.web.error;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Единый формат ответа об ошибке (все статусы 4xx/5xx, в том числе из фильтров безопасности).
 *
 * @param status    HTTP-статус
 * @param code      машинный код ({@link ErrorCode} или код доменного исключения)
 * @param message   текст для пользователя: что случилось и что сделать
 * @param path      адрес запроса
 * @param timestamp момент ошибки (ISO 8601)
 */
@Schema(name = "ApiError", description = "Ответ об ошибке")
public record ApiErrorResponse(
        @Schema(example = "409") int status,
        @Schema(example = "INSUFFICIENT_FUNDS") String code,
        @Schema(example = "Не хватает 1 400 Б: программа стоит 12 000 Б, на счёте 10 600 Б.") String message,
        @Schema(example = "/api/v1/programs/6/redemptions") String path,
        OffsetDateTime timestamp) {
}
