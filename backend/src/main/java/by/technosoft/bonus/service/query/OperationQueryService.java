package by.technosoft.bonus.service.query;

import static by.technosoft.bonus.repository.OperationSpecifications.all;
import static by.technosoft.bonus.repository.OperationSpecifications.createdBefore;
import static by.technosoft.bonus.repository.OperationSpecifications.createdFrom;
import static by.technosoft.bonus.repository.OperationSpecifications.department;
import static by.technosoft.bonus.repository.OperationSpecifications.employee;
import static by.technosoft.bonus.repository.OperationSpecifications.idIn;
import static by.technosoft.bonus.repository.OperationSpecifications.statusIn;
import static by.technosoft.bonus.repository.OperationSpecifications.type;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import by.technosoft.bonus.config.BonusProperties;
import by.technosoft.bonus.domain.Operation;
import by.technosoft.bonus.domain.OperationReview;
import by.technosoft.bonus.domain.OperationStatus;
import by.technosoft.bonus.domain.OperationType;
import by.technosoft.bonus.domain.ReviewDecision;
import by.technosoft.bonus.repository.OperationRepository;
import by.technosoft.bonus.repository.OperationReviewRepository;
import by.technosoft.bonus.service.PointsFormat;
import by.technosoft.bonus.service.exception.DomainValidationException;
import by.technosoft.bonus.service.exception.ObjectNotFoundException;

/**
 * Чтение журнала операций: поиск с фильтрами (HR, финотдел), очередь контроля финотдела,
 * загрузка операций после команд (начисление, корректировка, подтверждение).
 */
@Service
@Transactional(readOnly = true)
public class OperationQueryService {

    private static final Sort OLDEST_FIRST = Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"));

    /**
     * Операция с пояснением для людей.
     *
     * @param operation операция (владелец счёта и автор загружены)
     * @param comment   комментарий финотдела или причина согласования; {@code null} — нет
     * @param disputed  финотдел пометил проведённую операцию как спорную
     */
    public record OperationEntry(Operation operation, String comment, boolean disputed) {
    }

    /**
     * Фильтр журнала операций; {@code null} — без ограничения.
     *
     * @param from дата начала периода включительно
     * @param to   дата конца периода включительно
     * @param text подстрока номера, ФИО или причины
     */
    public record OperationFilter(Long employeeId, Long departmentId, OperationType type,
                                  Set<OperationStatus> statuses, LocalDate from, LocalDate to, String text) {
    }

    private final OperationRepository operationRepository;
    private final OperationReviewRepository reviewRepository;
    private final BonusProperties properties;
    private final Clock clock;

    public OperationQueryService(OperationRepository operationRepository,
                                 OperationReviewRepository reviewRepository,
                                 BonusProperties properties,
                                 Clock clock) {
        this.operationRepository = operationRepository;
        this.reviewRepository = reviewRepository;
        this.properties = properties;
        this.clock = clock;
    }

    /** Журнал операций с фильтрами, новые сверху. */
    public List<OperationEntry> search(OperationFilter filter) {
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            throw new DomainValidationException("Дата начала периода позже даты окончания.");
        }
        ZoneId zone = clock.getZone();
        List<Operation> ops = operationRepository.findAll(all()
                        .and(employee(filter.employeeId()))
                        .and(department(filter.departmentId()))
                        .and(type(filter.type()))
                        .and(statusIn(filter.statuses()))
                        .and(createdFrom(filter.from() == null ? null : BonusPeriods.startOfDay(filter.from(), zone)))
                        .and(createdBefore(filter.to() == null ? null
                                : BonusPeriods.startOfDay(filter.to().plusDays(1), zone))),
                AccountQueryService.NEWEST_FIRST);
        return describe(ops.stream().filter(containsText(filter.text())).toList());
    }

    /** Операция по id (после команды), владелец и автор загружены. */
    public OperationEntry get(Long id) {
        List<OperationEntry> found = getAll(List.of(id));
        if (found.isEmpty()) {
            throw new ObjectNotFoundException("Операция не найдена. Обновите журнал.");
        }
        return found.get(0);
    }

    /** Операции по списку id в порядке списка. */
    public List<OperationEntry> getAll(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, OperationEntry> byId = new HashMap<>();
        describe(operationRepository.findAll(idIn(ids), OLDEST_FIRST))
                .forEach(e -> byId.put(e.operation().getId(), e));
        return ids.stream().map(byId::get).filter(e -> e != null).toList();
    }

    /**
     * Очередь контроля финотдела: операции на согласовании (старые сверху), затем проведённые
     * операции, помеченные спорными.
     */
    public List<OperationEntry> controlQueue() {
        List<Operation> queue = new ArrayList<>(operationRepository.findAll(
                statusIn(Set.of(OperationStatus.PENDING)), OLDEST_FIRST));
        List<Long> disputedIds = reviewRepository.findOperationIdsByDecision(ReviewDecision.DISPUTED);
        if (!disputedIds.isEmpty()) {
            queue.addAll(operationRepository.findAll(
                    idIn(disputedIds).and(statusIn(OperationStatus.SETTLED)), OLDEST_FIRST));
        }
        return describe(queue).stream()
                .filter(e -> e.operation().getStatus() == OperationStatus.PENDING || e.disputed())
                .toList();
    }

    /**
     * Добавляет к операциям последнее решение финотдела и пояснение (одним запросом);
     * порядок списка сохраняется. Используется и другими запросами (история счёта, UC2) — DRY.
     */
    public List<OperationEntry> describe(List<Operation> ops) {
        if (ops.isEmpty()) {
            return List.of();
        }
        Map<Long, OperationReview> lastReview = new HashMap<>();
        for (OperationReview review : reviewRepository.findByOperationIds(
                ops.stream().map(Operation::getId).toList())) {
            lastReview.put(review.getOperation().getId(), review); // список по возрастанию времени
        }
        String limitComment = "Превышен лимит " + PointsFormat.format(properties.accrualLimit()) + " на сотрудника";
        return ops.stream().map(op -> {
            OperationReview review = lastReview.get(op.getId());
            boolean disputed = review != null && review.getDecision() == ReviewDecision.DISPUTED
                    && op.getStatus().isSettled();
            String comment = review != null && review.getComment() != null ? review.getComment()
                    : pendingComment(op, limitComment);
            return new OperationEntry(op, comment, disputed);
        }).toList();
    }

    /**
     * Поиск подстроки в номере, ФИО сотрудника или причине без учёта регистра. Выполняется в Java:
     * функция lower() PostgreSQL при правилах сортировки «C» не переводит кириллицу в нижний регистр,
     * поэтому «Новик» не нашёлся бы по «новик». Остальные условия отбирают строки в БД.
     */
    static Predicate<Operation> containsText(String query) {
        if (query == null || query.isBlank()) {
            return op -> true;
        }
        String q = query.trim().toLowerCase(Locale.ROOT);
        return op -> (op.getNumber() + " " + op.getAccount().getEmployee().getFullName() + " " + op.getReason())
                .toLowerCase(Locale.ROOT).contains(q);
    }

    private static String pendingComment(Operation op, String limitComment) {
        if (op.getStatus() != OperationStatus.PENDING) {
            return null;
        }
        return op.getType() == OperationType.CORRECTION
                ? "Баланс сотрудника стал бы отрицательным: нужно согласование финотдела"
                : limitComment;
    }
}
