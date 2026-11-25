package by.technosoft.bonus.repository;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import by.technosoft.bonus.domain.Notification;

/** Доступ к уведомлениям пользователей. */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** Отмечает все непрочитанные уведомления пользователя прочитанными; повторный вызов ничего не меняет. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update notification set read_at = :at where user_id = :userId and read_at is null",
            nativeQuery = true)
    int markAllRead(@Param("userId") long userId, @Param("at") OffsetDateTime at);
}
