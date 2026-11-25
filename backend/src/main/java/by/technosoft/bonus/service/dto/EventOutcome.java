package by.technosoft.bonus.service.dto;

/** Итог обработки бизнес-события (UC4). */
public enum EventOutcome {
    /** Создано начисление. */
    ACCRUED,
    /** Событие обработано, но бонус не положен (ниже порога, BR-5). */
    NO_BONUS,
    /** Событие уже обработано ранее — повторная обработка пропущена (NFR-4). */
    SKIPPED,
    /** Ошибка: событие переведено в failed. */
    FAILED
}
