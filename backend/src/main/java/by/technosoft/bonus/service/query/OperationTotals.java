package by.technosoft.bonus.service.query;

import java.util.Collection;

import by.technosoft.bonus.domain.Operation;
import by.technosoft.bonus.domain.OperationStatus;
import by.technosoft.bonus.domain.OperationType;

/**
 * Итоги по набору операций — единые формулы для экранов «Мой баланс», «Моя команда»,
 * отчётов (UC10) и аналитики (UC11) (DRY).
 */
public final class OperationTotals {

    private OperationTotals() {
    }

    /** Начислено: положительные проведённые суммы (начисления и корректировки вверх). */
    public static int accrued(Collection<Operation> ops) {
        return ops.stream()
                .filter(o -> o.getAmount() > 0 && o.getStatus().isSettled() && o.getType() != OperationType.WRITEOFF)
                .mapToInt(Operation::getAmount)
                .sum();
    }

    /** Потрачено на программы мотивации (положительное число). */
    public static int spent(Collection<Operation> ops) {
        return -ops.stream()
                .filter(o -> o.getType() == OperationType.WRITEOFF && o.getStatus() == OperationStatus.WRITTEN_OFF)
                .mapToInt(Operation::getAmount)
                .sum();
    }

    /** Ожидает согласования финотдела: положительные суммы в статусе pending. */
    public static int pending(Collection<Operation> ops) {
        return ops.stream()
                .filter(o -> o.getStatus() == OperationStatus.PENDING && o.getAmount() > 0)
                .mapToInt(Operation::getAmount)
                .sum();
    }

    /** Сумма проведённых операций — баланс по журналу (инвариант BR-6). */
    public static int settled(Collection<Operation> ops) {
        return ops.stream().filter(o -> o.getStatus().isSettled()).mapToInt(Operation::getAmount).sum();
    }
}
