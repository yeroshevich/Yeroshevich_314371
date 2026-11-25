package by.technosoft.bonus.domain;

import java.util.Arrays;

/**
 * Перечисление, которое хранится в БД строковым кодом из CHECK-ограничения схемы
 * (например, {@code 'written_off'}), а не именем константы Java.
 */
public interface PersistentEnum {

    /** Значение столбца в БД. */
    String dbValue();

    /** Поиск константы по значению столбца. */
    static <E extends Enum<E> & PersistentEnum> E fromDb(Class<E> type, String value) {
        return Arrays.stream(type.getEnumConstants())
                .filter(c -> c.dbValue().equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Неизвестное значение " + type.getSimpleName() + ": " + value));
    }
}
