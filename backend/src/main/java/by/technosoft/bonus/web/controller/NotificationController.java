package by.technosoft.bonus.web.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import by.technosoft.bonus.service.notification.NotificationService;
import by.technosoft.bonus.web.dto.NotificationDto;
import by.technosoft.bonus.web.mapper.NotificationMapper;
import by.technosoft.bonus.web.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Уведомления текущего пользователя (колокольчик в шапке). */
@RestController
@RequestMapping(ApiPaths.V1 + "/notifications")
@Tag(name = "Уведомления")
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationMapper mapper;

    public NotificationController(NotificationService notificationService, NotificationMapper mapper) {
        this.notificationService = notificationService;
        this.mapper = mapper;
    }

    @GetMapping
    @Operation(summary = "Мои уведомления", description = "Новые сверху.")
    public List<NotificationDto> list(@AuthenticationPrincipal AuthenticatedUser me) {
        return notificationService.list(me.userId()).stream().map(mapper::toDto).toList();
    }

    @PostMapping("/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Отметить все прочитанными", description = "Идемпотентно.")
    public void markAllRead(@AuthenticationPrincipal AuthenticatedUser me) {
        notificationService.markAllRead(me.userId());
    }
}
