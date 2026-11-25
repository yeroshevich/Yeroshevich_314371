package by.technosoft.bonus.domain;

/** Состояние учётной записи (app_user.status). */
public enum UserStatus implements PersistentEnum {
    /** Вход разрешён. */
    ACTIVE("active"),
    /** Учётная запись заблокирована администратором. */
    BLOCKED("blocked");

    private final String dbValue;

    UserStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String dbValue() {
        return dbValue;
    }
}
