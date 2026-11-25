package by.technosoft.bonus.domain.converter;

import by.technosoft.bonus.domain.EventStatus;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class EventStatusConverter extends PersistentEnumConverter<EventStatus> {

    public EventStatusConverter() {
        super(EventStatus.class);
    }
}
