package by.technosoft.bonus.domain;

/**
 * Зарезервированный идентификатор операции и её номер для людей.
 * Оба берутся из одного значения последовательности operation_id_seq,
 * поэтому номер «ОП-000123» всегда соответствует id = 123 (как в демо-данных).
 *
 * @param id     первичный ключ операции
 * @param number номер в формате «ОП-000123»
 */
public record OperationNumber(long id, String number) {
}
