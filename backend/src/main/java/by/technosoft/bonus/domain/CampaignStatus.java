package by.technosoft.bonus.domain;

/** Статус бонусной кампании (campaign.status, UC9). */
public enum CampaignStatus implements PersistentEnum {
    /** Запланирована. */
    PLANNED("planned"),
    /** Активна: коэффициент применяется к начислениям. */
    ACTIVE("active"),
    /** Требуется утверждение (бюджет не выделен). */
    NEEDS_APPROVAL("needs_approval"),
    /** Завершена. */
    FINISHED("finished");

    private final String dbValue;

    CampaignStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String dbValue() {
        return dbValue;
    }
}
