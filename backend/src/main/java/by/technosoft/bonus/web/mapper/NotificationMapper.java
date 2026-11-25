package by.technosoft.bonus.web.mapper;

import static by.technosoft.bonus.web.mapper.ApiValues.id;

import org.springframework.stereotype.Component;

import by.technosoft.bonus.domain.Notification;
import by.technosoft.bonus.web.dto.NotificationDto;

/** Уведомление → JSON. */
@Component
public class NotificationMapper {

    public NotificationDto toDto(Notification n) {
        return new NotificationDto(id(n.getId()), n.getTitle(), n.getText(), n.getCreatedAt(), n.isRead());
    }
}
