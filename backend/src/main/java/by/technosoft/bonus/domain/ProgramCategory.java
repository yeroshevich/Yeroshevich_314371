package by.technosoft.bonus.domain;

/** Категория программы мотивации (program.category). */
public enum ProgramCategory implements PersistentEnum {
    /** Подарки. */
    GIFT("gift"),
    /** Обучение. */
    EDUCATION("education"),
    /** Мерч. */
    MERCH("merch"),
    /** Отдых. */
    DAYOFF("dayoff"),
    /** Здоровье. */
    HEALTH("health"),
    /** События. */
    EVENT("event");

    private final String dbValue;

    ProgramCategory(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String dbValue() {
        return dbValue;
    }
}
