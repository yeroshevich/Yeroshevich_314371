package by.technosoft.bonus.service.notification;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import by.technosoft.bonus.domain.Notification;
import by.technosoft.bonus.repository.NotificationRepository;

/** Уведомления пользователя (колокольчик в шапке): список и отметка «прочитано». */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final Clock clock;

    public NotificationService(NotificationRepository notificationRepository, Clock clock) {
        this.notificationRepository = notificationRepository;
        this.clock = clock;
    }

    /** Уведомления пользователя, новые сверху. */
    @Transactional(readOnly = true)
    public List<Notification> list(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /** Отмечает все уведомления прочитанными (идемпотентно); возвращает число изменённых. */
    @Transactional
    public int markAllRead(Long userId) {
        return notificationRepository.markAllRead(userId, OffsetDateTime.now(clock));
    }
}
