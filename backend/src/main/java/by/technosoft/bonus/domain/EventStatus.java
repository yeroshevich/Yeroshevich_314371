package by.technosoft.bonus.domain;

/** Статус бизнес-события в очереди (business_event.status, UC4). */
public enum EventStatus implements PersistentEnum {
    /** Получено и ещё не обработано. */
    NEW("new"),
    /** Обработано: начисление проведено или не положено по BR-5. */
    PROCESSED("processed"),
    /** Ошибка обработки: событие отложено, причина — в error_message. */
    FAILED("failed");

    private final String dbValue;

    EventStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String dbValue() {
        return dbValue;
    }
}
