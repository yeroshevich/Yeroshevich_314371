package by.technosoft.bonus.domain;

/** Состояние бонусного счёта (bonus_account.status). */
public enum AccountStatus implements PersistentEnum {
    /** Счёт доступен. */
    ACTIVE("active"),
    /** Счёт заблокирован HR: баланс недоступен (альт. поток UC1). */
    BLOCKED("blocked");

    private final String dbValue;

    AccountStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String dbValue() {
        return dbValue;
    }
}
