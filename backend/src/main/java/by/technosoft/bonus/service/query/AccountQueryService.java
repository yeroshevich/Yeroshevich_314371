package by.technosoft.bonus.service.query;

import static by.technosoft.bonus.repository.OperationSpecifications.createdBefore;
import static by.technosoft.bonus.repository.OperationSpecifications.createdFrom;
import static by.technosoft.bonus.repository.OperationSpecifications.employee;
import static by.technosoft.bonus.repository.OperationSpecifications.type;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import by.technosoft.bonus.domain.BonusAccount;
import by.technosoft.bonus.domain.Operation;
import by.technosoft.bonus.domain.OperationType;
import by.technosoft.bonus.repository.BonusAccountRepository;
import by.technosoft.bonus.repository.OperationRepository;
import by.technosoft.bonus.service.exception.AccountBlockedException;
import by.technosoft.bonus.service.exception.DomainValidationException;
import by.technosoft.bonus.service.exception.ObjectNotFoundException;
import by.technosoft.bonus.service.query.OperationQueryService.OperationEntry;

/** Чтение бонусного счёта сотрудника: «Мой баланс» (UC1) и «История операций» (UC2). */
@Service
@Transactional(readOnly = true)
public class AccountQueryService {

    /** Новые операции сверху; при равном времени — по номеру. */
    static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private static final int RECENT_COUNT = 5;
    private static final int HISTORY_MONTHS = 6;

    /**
     * @param account счёт
     * @param pending сумма начислений на согласовании
     */
    public record AccountState(BonusAccount account, int pending) {
    }

    /** Баланс на конец месяца (последний элемент — текущий баланс). */
    public record MonthBalance(YearMonth month, int balance) {
    }

    /** Сводка для экрана «Мой баланс». */
    public record AccountOverview(AccountState state, int accruedThisMonth, int spentThisMonth,
                                  List<OperationEntry> recent, List<MonthBalance> history) {
    }

    private final BonusAccountRepository accountRepository;
    private final OperationRepository operationRepository;
    private final OperationQueryService operationQueryService;
    private final Clock clock;

    public AccountQueryService(BonusAccountRepository accountRepository, OperationRepository operationRepository,
                               OperationQueryService operationQueryService, Clock clock) {
        this.accountRepository = accountRepository;
        this.operationRepository = operationRepository;
        this.operationQueryService = operationQueryService;
        this.clock = clock;
    }

    /**
     * UC1 «Просмотр баланса»: баланс, сумма на согласовании, итоги текущего месяца,
     * последние операции и динамика баланса за 6 месяцев. Заблокированный счёт — отказ (альт. поток UC1).
     */
    public AccountOverview overview(Long employeeId) {
        BonusAccount account = requireAccount(employeeId);
        if (!account.isActive()) {
            throw new AccountBlockedException("Баланс недоступен, обратитесь к HR.");
        }
        List<Operation> all = operationRepository.findAll(employee(employeeId), NEWEST_FIRST);
        ZoneId zone = clock.getZone();
        YearMonth current = YearMonth.now(clock);
        List<Operation> thisMonth = all.stream()
                .filter(o -> !o.getCreatedAt().isBefore(BonusPeriods.startOfDay(current.atDay(1), zone)))
                .toList();

        List<MonthBalance> history = new ArrayList<>();
        for (int i = HISTORY_MONTHS - 1; i >= 1; i--) {
            YearMonth month = current.minusMonths(i);
            var end = BonusPeriods.startOfDay(month.plusMonths(1).atDay(1), zone);
            history.add(new MonthBalance(month, OperationTotals.settled(
                    all.stream().filter(o -> o.getCreatedAt().isBefore(end)).toList())));
        }
        history.add(new MonthBalance(current, account.getBalance()));

        return new AccountOverview(new AccountState(account, OperationTotals.pending(all)),
                OperationTotals.accrued(thisMonth), OperationTotals.spent(thisMonth),
                operationQueryService.describe(all.stream().limit(RECENT_COUNT).toList()), List.copyOf(history));
    }

    /** Текущее состояние счёта (после списания на программу, UC3). */
    public AccountState state(Long employeeId) {
        BonusAccount account = requireAccount(employeeId);
        List<Operation> all = operationRepository.findAll(employee(employeeId), NEWEST_FIRST);
        return new AccountState(account, OperationTotals.pending(all));
    }

    /**
     * UC2 «История операций»: операции своего счёта за период [from; to] (даты включительно)
     * и выбранного типа; любой параметр может отсутствовать.
     */
    public List<OperationEntry> history(Long employeeId, LocalDate from, LocalDate to, OperationType operationType) {
        requireAccount(employeeId);
        if (from != null && to != null && from.isAfter(to)) {
            throw new DomainValidationException("Дата начала периода позже даты окончания.");
        }
        ZoneId zone = clock.getZone();
        return operationQueryService.describe(operationRepository.findAll(employee(employeeId)
                        .and(type(operationType))
                        .and(createdFrom(from == null ? null : BonusPeriods.startOfDay(from, zone)))
                        .and(createdBefore(to == null ? null : BonusPeriods.startOfDay(to.plusDays(1), zone))),
                NEWEST_FIRST));
    }

    private BonusAccount requireAccount(Long employeeId) {
        if (employeeId == null) {
            throw new ObjectNotFoundException("К учётной записи не привязан бонусный счёт.");
        }
        return accountRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new ObjectNotFoundException("К учётной записи не привязан бонусный счёт."));
    }
}
