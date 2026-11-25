package by.technosoft.bonus.service.event;

/** Вид значимого действия с операцией; подпись совпадает с записями журнала аудита. */
public enum OperationAction {
    MANUAL_ACCRUAL("Ручное начисление"),
    AUTO_ACCRUAL("Автоначисление по событию"),
    REDEMPTION("Использование баллов"),
    CORRECTION("Корректировка"),
    CANCELLATION("Отмена начисления"),
    APPROVAL("Подтверждение начисления"),
    BLOCKING("Блокировка начисления");

    private final String auditLabel;

    OperationAction(String auditLabel) {
        this.auditLabel = auditLabel;
    }

    public String auditLabel() {
        return auditLabel;
    }
}
