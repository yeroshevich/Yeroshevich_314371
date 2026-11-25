package by.technosoft.bonus.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Статус операции (operation.status) и допустимые переходы жизненного цикла.
 * <pre>
 *   [создание] → ACCRUED | WRITTEN_OFF | PENDING | CORRECTED | CANCELLED
 *   PENDING   → ACCRUED | CORRECTED | CANCELLED   (подтверждение финотделом)
 *   PENDING   → BLOCKED                           (блокировка финотделом)
 *   ACCRUED   → CANCELLED                         (заменена корректировкой или отменена, UC7)
 *   CORRECTED → CANCELLED                         (повторная корректировка, UC7)
 *   WRITTEN_OFF, BLOCKED, CANCELLED — конечные состояния
 * </pre>
 */
public enum OperationStatus implements PersistentEnum {
    /** Начислено: сумма учтена в балансе. */
    ACCRUED("accrued"),
    /** Списано на программу мотивации: сумма учтена в балансе. */
    WRITTEN_OFF("written_off"),
    /** Корректировка проведена: новая сумма учтена в балансе. */
    CORRECTED("corrected"),
    /** На согласовании финотдела: баланс не изменён. */
    PENDING("pending"),
    /** Заблокировано финотделом: баланс не изменён. */
    BLOCKED("blocked"),
    /** Отменено (заменено корректировкой): в балансе не учитывается. */
    CANCELLED("cancelled");

    /** Статусы, суммы которых образуют баланс счёта (BR-6). */
    public static final Set<OperationStatus> SETTLED = EnumSet.of(ACCRUED, WRITTEN_OFF, CORRECTED);

    private final String dbValue;

    OperationStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String dbValue() {
        return dbValue;
    }

    /** Учитывается ли сумма операции в этом статусе в балансе счёта. */
    public boolean isSettled() {
        return SETTLED.contains(this);
    }

    /** Допустим ли переход из текущего статуса в {@code target}. */
    public boolean canTransitionTo(OperationStatus target) {
        return switch (this) {
            case PENDING -> target == ACCRUED || target == CORRECTED || target == CANCELLED || target == BLOCKED;
            case ACCRUED, CORRECTED -> target == CANCELLED;
            case WRITTEN_OFF, BLOCKED, CANCELLED -> false;
        };
    }
}
