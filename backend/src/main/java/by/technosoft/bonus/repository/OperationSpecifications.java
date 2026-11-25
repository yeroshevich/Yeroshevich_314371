package by.technosoft.bonus.repository;

import java.time.OffsetDateTime;
import java.util.Collection;

import org.springframework.data.jpa.domain.Specification;

import by.technosoft.bonus.domain.Operation;
import by.technosoft.bonus.domain.OperationStatus;
import by.technosoft.bonus.domain.OperationType;

/**
 * Условия поиска операций (Specification). Каждое условие независимо, {@code null}-аргумент
 * означает «без ограничения»; фильтр журнала собирается из них через {@code and} — новое условие
 * добавляется без изменения существующих (OCP).
 */
public final class OperationSpecifications {

    private OperationSpecifications() {
    }

    /** Без ограничений — начальное звено цепочки {@code and}. */
    public static Specification<Operation> all() {
        return (root, query, cb) -> cb.conjunction();
    }

    public static Specification<Operation> idIn(Collection<Long> ids) {
        return ids == null ? null : (root, query, cb) -> root.get("id").in(ids);
    }

    public static Specification<Operation> employee(Long employeeId) {
        return employeeId == null ? null
                : (root, query, cb) -> cb.equal(root.get("account").get("employee").get("id"), employeeId);
    }

    public static Specification<Operation> employeeIn(Collection<Long> employeeIds) {
        return employeeIds == null ? null
                : (root, query, cb) -> root.get("account").get("employee").get("id").in(employeeIds);
    }

    public static Specification<Operation> department(Long departmentId) {
        return departmentId == null ? null
                : (root, query, cb) -> cb.equal(
                        root.get("account").get("employee").get("department").get("id"), departmentId);
    }

    public static Specification<Operation> type(OperationType type) {
        return type == null ? null : (root, query, cb) -> cb.equal(root.get("type"), type);
    }

    public static Specification<Operation> statusIn(Collection<OperationStatus> statuses) {
        return statuses == null || statuses.isEmpty() ? null : (root, query, cb) -> root.get("status").in(statuses);
    }

    public static Specification<Operation> campaign(Long campaignId) {
        return campaignId == null ? null
                : (root, query, cb) -> cb.equal(root.get("campaign").get("id"), campaignId);
    }

    /** Создана не раньше {@code from} (включительно). */
    public static Specification<Operation> createdFrom(OffsetDateTime from) {
        return from == null ? null
                : (root, query, cb) -> cb.greaterThanOrEqualTo(root.<OffsetDateTime>get("createdAt"), from);
    }

    /** Создана раньше {@code before} (исключительно). */
    public static Specification<Operation> createdBefore(OffsetDateTime before) {
        return before == null ? null
                : (root, query, cb) -> cb.lessThan(root.<OffsetDateTime>get("createdAt"), before);
    }
}
