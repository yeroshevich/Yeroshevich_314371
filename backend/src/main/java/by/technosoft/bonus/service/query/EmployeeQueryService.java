package by.technosoft.bonus.service.query;

import static by.technosoft.bonus.repository.OperationSpecifications.createdFrom;
import static by.technosoft.bonus.repository.OperationSpecifications.employeeIn;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import by.technosoft.bonus.domain.AppUser;
import by.technosoft.bonus.domain.BonusAccount;
import by.technosoft.bonus.domain.Employee;
import by.technosoft.bonus.domain.KpiResult;
import by.technosoft.bonus.domain.Operation;
import by.technosoft.bonus.domain.RoleCode;
import by.technosoft.bonus.repository.BonusAccountRepository;
import by.technosoft.bonus.repository.EmployeeRepository;
import by.technosoft.bonus.repository.KpiResultRepository;
import by.technosoft.bonus.repository.OperationRepository;
import by.technosoft.bonus.service.AccessPolicy;

/**
 * Чтение сотрудников с учётом области ответственности (SEC-3): руководитель видит только
 * свою команду, HR, финотдел и администратор — всех сотрудников.
 */
@Service
@Transactional(readOnly = true)
public class EmployeeQueryService {

    /** Сотрудник и выполнение KPI за текущий период, %. */
    public record EmployeeRow(Employee employee, int kpiCompletion) {
    }

    /** Строка экрана «Моя команда»: сотрудник, KPI, счёт и начислено за текущий месяц. */
    public record TeamMemberRow(Employee employee, int kpiCompletion, BonusAccount account, int accruedThisMonth) {
    }

    private final AccessPolicy accessPolicy;
    private final EmployeeRepository employeeRepository;
    private final BonusAccountRepository accountRepository;
    private final OperationRepository operationRepository;
    private final KpiResultRepository kpiResultRepository;
    private final Clock clock;

    public EmployeeQueryService(AccessPolicy accessPolicy,
                                EmployeeRepository employeeRepository,
                                BonusAccountRepository accountRepository,
                                OperationRepository operationRepository,
                                KpiResultRepository kpiResultRepository,
                                Clock clock) {
        this.accessPolicy = accessPolicy;
        this.employeeRepository = employeeRepository;
        this.accountRepository = accountRepository;
        this.operationRepository = operationRepository;
        this.kpiResultRepository = kpiResultRepository;
        this.clock = clock;
    }

    /**
     * Сотрудники, доступные пользователю.
     *
     * @param teamOnly только непосредственные подчинённые пользователя; руководитель всегда видит
     *                 только свою команду, независимо от параметра (SEC-3)
     */
    public List<EmployeeRow> visibleEmployees(Long actorUserId, boolean teamOnly) {
        AppUser actor = accessPolicy.requireActiveUser(actorUserId);
        accessPolicy.requireRole(actor, RoleCode.MANAGER, RoleCode.HR, RoleCode.FINANCE, RoleCode.ADMIN);
        List<Employee> employees = teamOnly
                ? employeeRepository.findWithDepartmentByManagerIdOrderByFullName(actor.getEmployee().getId())
                : scope(actor);
        Map<Long, Integer> kpi = kpiCompletion();
        return employees.stream().map(e -> new EmployeeRow(e, kpi.getOrDefault(e.getId(), 0))).toList();
    }

    /** Команда руководителя или все сотрудники для HR — с балансами и начислениями месяца. */
    public List<TeamMemberRow> team(Long actorUserId) {
        AppUser actor = accessPolicy.requireActiveUser(actorUserId);
        accessPolicy.requireRole(actor, RoleCode.MANAGER, RoleCode.HR);
        List<Employee> employees = scope(actor);
        List<Long> ids = employees.stream().map(Employee::getId).toList();
        Map<Long, BonusAccount> accounts = accountRepository.findByEmployeeIdIn(ids).stream()
                .collect(Collectors.toMap(a -> a.getEmployee().getId(), Function.identity()));
        LocalDate monthStart = YearMonth.now(clock).atDay(1);
        Map<Long, List<Operation>> monthOps = operationRepository.findAll(
                        employeeIn(ids).and(createdFrom(BonusPeriods.startOfDay(monthStart, clock.getZone()))),
                        AccountQueryService.NEWEST_FIRST).stream()
                .collect(Collectors.groupingBy(o -> o.getAccount().getEmployee().getId()));
        Map<Long, Integer> kpi = kpiCompletion();
        return employees.stream()
                .map(e -> new TeamMemberRow(e, kpi.getOrDefault(e.getId(), 0), accounts.get(e.getId()),
                        OperationTotals.accrued(monthOps.getOrDefault(e.getId(), List.of()))))
                .toList();
    }

    private List<Employee> scope(AppUser actor) {
        return actor.getRoleCode() == RoleCode.MANAGER
                ? employeeRepository.findWithDepartmentByManagerIdOrderByFullName(actor.getEmployee().getId())
                : employeeRepository.findAllWithDepartment();
    }

    /** Среднее выполнение KPI сотрудника за периоды, содержащие сегодняшнюю дату, % (целое). */
    private Map<Long, Integer> kpiCompletion() {
        return kpiResultRepository.findCovering(LocalDate.now(clock)).stream()
                .collect(Collectors.groupingBy(r -> r.getEmployee().getId(),
                        Collectors.collectingAndThen(Collectors.toList(), EmployeeQueryService::average)));
    }

    private static int average(List<KpiResult> results) {
        BigDecimal sum = results.stream().map(KpiResult::getValuePct).reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(results.size()), 0, RoundingMode.HALF_UP).intValue();
    }
}
