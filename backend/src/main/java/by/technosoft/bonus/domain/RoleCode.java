package by.technosoft.bonus.domain;

/** Код роли ролевой модели (role.code, SEC-3). */
public enum RoleCode implements PersistentEnum {
    /** Сотрудник. */
    EMPLOYEE("employee"),
    /** Руководитель. */
    MANAGER("manager"),
    /** HR. */
    HR("hr"),
    /** Финансовый отдел. */
    FINANCE("finance"),
    /** Администратор. */
    ADMIN("admin");

    private final String dbValue;

    RoleCode(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String dbValue() {
        return dbValue;
    }
}
