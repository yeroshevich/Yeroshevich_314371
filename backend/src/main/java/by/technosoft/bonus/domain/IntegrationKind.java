package by.technosoft.bonus.domain;

/** Вид внешней системы (integration.kind, UC13). */
public enum IntegrationKind implements PersistentEnum {
    /** Кадровая система. */
    HRIS("hris"),
    /** ERP. */
    ERP("erp"),
    /** Active Directory. */
    AD("ad"),
    /** Сервис уведомлений. */
    NOTIFICATIONS("notifications");

    private final String dbValue;

    IntegrationKind(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String dbValue() {
        return dbValue;
    }
}
