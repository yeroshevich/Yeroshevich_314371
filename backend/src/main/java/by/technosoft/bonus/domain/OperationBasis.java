package by.technosoft.bonus.domain;

/** Основание операции (operation.basis). */
public enum OperationBasis implements PersistentEnum {
    /** Выполнение KPI. */
    KPI("kpi"),
    /** Проектная задача. */
    PROJECT("project"),
    /** Особое достижение. */
    ACHIEVEMENT("achievement"),
    /** Бонусная кампания. */
    CAMPAIGN("campaign"),
    /** Программа мотивации. */
    PROGRAM("program"),
    /** Корректировка. */
    CORRECTION("correction");

    private final String dbValue;

    OperationBasis(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String dbValue() {
        return dbValue;
    }
}
