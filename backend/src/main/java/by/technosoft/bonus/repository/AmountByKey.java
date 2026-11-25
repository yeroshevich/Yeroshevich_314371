package by.technosoft.bonus.repository;

/**
 * Сумма баллов, сгруппированная по ключу (например, по кампании) — проекция агрегирующих запросов.
 *
 * @param key    идентификатор группы
 * @param amount сумма баллов
 */
public record AmountByKey(Long key, Long amount) {
}
