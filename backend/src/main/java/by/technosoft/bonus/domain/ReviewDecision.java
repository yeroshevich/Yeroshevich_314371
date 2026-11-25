package by.technosoft.bonus.domain;

/** Решение финансового отдела по операции (operation_review.decision). */
public enum ReviewDecision implements PersistentEnum {
    /** Подтверждено. */
    APPROVED("approved"),
    /** Заблокировано. */
    BLOCKED("blocked"),
    /** Помечено как спорное. */
    DISPUTED("disputed");

    private final String dbValue;

    ReviewDecision(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String dbValue() {
        return dbValue;
    }
}
