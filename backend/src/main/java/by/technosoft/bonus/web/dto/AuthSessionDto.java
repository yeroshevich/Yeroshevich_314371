package by.technosoft.bonus.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Результат входа (TS: {@code AuthSession}): JWT и профиль. */
@Schema(name = "AuthSession")
public record AuthSessionDto(
        @Schema(description = "JWT HS256, передаётся в заголовке Authorization: Bearer; срок — 8 ч") String token,
        UserDto user) {
}
