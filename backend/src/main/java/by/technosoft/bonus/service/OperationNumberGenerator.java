package by.technosoft.bonus.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import by.technosoft.bonus.domain.OperationNumber;

/**
 * Выдаёт идентификатор и номер новой операции «ОП-000123».
 * Значение берётся из последовательности столбца operation.id (как в DML ЛР7:
 * {@code 'ОП-' || lpad(nextval(pg_get_serial_sequence('operation', 'id'))::text, 6, '0')}),
 * и то же значение становится первичным ключом — номер и id не расходятся.
 * nextval атомарна, поэтому параллельные транзакции получают разные номера;
 * при откате транзакции номер «сгорает» (пропуск в нумерации допустим).
 */
@Component
public class OperationNumberGenerator {

    static final String NEXT_VALUE_SQL = "select nextval(pg_get_serial_sequence('operation', 'id'))";

    private final JdbcTemplate jdbcTemplate;

    public OperationNumberGenerator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Резервирует следующий номер операции. */
    public OperationNumber next() {
        Long value = jdbcTemplate.queryForObject(NEXT_VALUE_SQL, Long.class);
        if (value == null) {
            throw new IllegalStateException("Последовательность operation.id не вернула значение");
        }
        return new OperationNumber(value, format(value));
    }

    /** Форматирует номер: «ОП-» и не менее шести цифр. */
    public static String format(long value) {
        return String.format("ОП-%06d", value);
    }
}
