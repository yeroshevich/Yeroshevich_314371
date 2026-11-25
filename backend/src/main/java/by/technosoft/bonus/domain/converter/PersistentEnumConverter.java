package by.technosoft.bonus.domain.converter;

import by.technosoft.bonus.domain.PersistentEnum;
import jakarta.persistence.AttributeConverter;

/**
 * Общий JPA-конвертер «enum ↔ строковый код схемы». Конкретные конвертеры
 * помечены {@code @Converter(autoApply = true)} и применяются ко всем полям своего типа.
 */
public abstract class PersistentEnumConverter<E extends Enum<E> & PersistentEnum>
        implements AttributeConverter<E, String> {

    private final Class<E> type;

    protected PersistentEnumConverter(Class<E> type) {
        this.type = type;
    }

    @Override
    public String convertToDatabaseColumn(E attribute) {
        return attribute == null ? null : attribute.dbValue();
    }

    @Override
    public E convertToEntityAttribute(String dbData) {
        return dbData == null ? null : PersistentEnum.fromDb(type, dbData);
    }
}
