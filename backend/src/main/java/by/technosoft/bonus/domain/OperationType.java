package by.technosoft.bonus.domain;

/** Тип операции по счёту (operation.type). */
public enum OperationType implements PersistentEnum {
    /** Начисление. */
    ACCRUAL("accrual"),
    /** Списание на программу мотивации. */
    WRITEOFF("writeoff"),
    /** Корректировка ранее проведённой операции (UC7). */
    CORRECTION("correction");

    private final String dbValue;

    OperationType(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String dbValue() {
        return dbValue;
    }
}
