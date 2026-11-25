package by.technosoft.bonus.web.dto;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/** Уведомление пользователя (TS: {@code Notification}). */
@Schema(name = "Notification")
public record NotificationDto(String id, String title, String text, OffsetDateTime createdAt, boolean read) {
}
