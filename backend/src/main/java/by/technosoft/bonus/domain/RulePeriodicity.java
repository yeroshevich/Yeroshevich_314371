package by.technosoft.bonus.domain;

/** Периодичность правила начисления (accrual_rule.periodicity). */
public enum RulePeriodicity implements PersistentEnum {
    /** Ежемесячно. */
    MONTH("month"),
    /** Ежеквартально. */
    QUARTER("quarter"),
    /** Ежегодно (например, годовщина работы). */
    YEAR("year"),
    /** По событию. */
    EVENT("event");

    private final String dbValue;

    RulePeriodicity(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String dbValue() {
        return dbValue;
    }
}
