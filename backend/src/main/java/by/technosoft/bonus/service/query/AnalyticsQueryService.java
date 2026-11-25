package by.technosoft.bonus.service.query;

import static by.technosoft.bonus.repository.OperationSpecifications.createdBefore;
import static by.technosoft.bonus.repository.OperationSpecifications.createdFrom;
import static by.technosoft.bonus.repository.OperationSpecifications.employeeIn;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import by.technosoft.bonus.domain.AppUser;
import by.technosoft.bonus.domain.Department;
import by.technosoft.bonus.domain.Employee;
import by.technosoft.bonus.domain.Operation;
import by.technosoft.bonus.domain.OperationStatus;
import by.technosoft.bonus.domain.OperationType;
import by.technosoft.bonus.domain.RoleCode;
import by.technosoft.bonus.repository.DepartmentRepository;
import by.technosoft.bonus.repository.EmployeeRepository;
import by.technosoft.bonus.repository.OperationRepository;
import by.technosoft.bonus.service.AccessPolicy;

/**
 * Анализ эффективности бонусной системы (UC11): средний бонус, конверсия программ мотивации,
 * распределение по подразделениям, динамика по месяцам, сравнение с предыдущим периодом той же длины.
 * Руководитель видит своё подразделение, HR — всю компанию.
 */
@Service
@Transactional(readOnly = true)
public class AnalyticsQueryService {

    private static final int MONTHLY_POINTS = 6;

    public record Comparison(int current, int previous) {
    }

    public record DepartmentComparison(String department, int current, int previous) {
    }

    public record MonthlyFlow(YearMonth month, int accrued, int spent) {
    }

    /**
     * @param currentStart  первый месяц текущего периода
     * @param previousStart первый месяц предыдущего периода
     * @param partial       данных меньше, чем нужно для полного сравнения (альт. поток UC11)
     */
    public record Dashboard(AnalyticsPeriod period, YearMonth currentStart, YearMonth previousStart,
                            Comparison averageBonus, Comparison programConversion,
                            Comparison totalAccrued, Comparison totalSpent,
                            List<DepartmentComparison> byDepartment, List<MonthlyFlow> monthly, boolean partial) {
    }

    private final AccessPolicy accessPolicy;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final OperationRepository operationRepository;
    private final Clock clock;

    public AnalyticsQueryService(AccessPolicy accessPolicy,
                                 EmployeeRepository employeeRepository,
                                 DepartmentRepository departmentRepository,
                                 OperationRepository operationRepository,
                                 Clock clock) {
        this.accessPolicy = accessPolicy;
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.operationRepository = operationRepository;
        this.clock = clock;
    }

    public Dashboard dashboard(Long actorUserId, AnalyticsPeriod period) {
        AppUser actor = accessPolicy.requireActiveUser(actorUserId);
        accessPolicy.requireRole(actor, RoleCode.HR, RoleCode.MANAGER);
        boolean manager = actor.getRoleCode() == RoleCode.MANAGER;
        Long ownDepartment = actor.getEmployee().getDepartment().getId();

        List<Employee> scope = manager ? employeeRepository.findByDepartmentId(ownDepartment)
                : employeeRepository.findAll();
        List<Long> ids = scope.stream().map(Employee::getId).toList();
        List<Department> departments = departmentRepository.findAll(Sort.by("id")).stream()
                .filter(d -> !manager || d.getId().equals(ownDepartment))
                .toList();

        ZoneId zone = clock.getZone();
        int months = period.months();
        YearMonth now = YearMonth.now(clock);
        YearMonth curStart = now.minusMonths(months - 1L);
        YearMonth prevStart = curStart.minusMonths(months);
        YearMonth monthlyStart = now.minusMonths(MONTHLY_POINTS - 1L);
        YearMonth loadFrom = prevStart.isBefore(monthlyStart) ? prevStart : monthlyStart;

        List<Operation> ops = ids.isEmpty() ? List.of() : operationRepository.findAll(
                employeeIn(ids).and(createdFrom(start(loadFrom, zone))), AccountQueryService.NEWEST_FIRST);
        List<Operation> cur = ops.stream().filter(inMonths(curStart, now.plusMonths(1), zone)).toList();
        List<Operation> prev = ops.stream().filter(inMonths(prevStart, curStart, zone)).toList();

        List<DepartmentComparison> byDepartment = departments.stream()
                .map(d -> new DepartmentComparison(d.getName(),
                        OperationTotals.accrued(cur.stream().filter(inDepartment(d)).toList()),
                        OperationTotals.accrued(prev.stream().filter(inDepartment(d)).toList())))
                .toList();

        List<MonthlyFlow> monthly = new ArrayList<>();
        for (YearMonth m = monthlyStart; !m.isAfter(now); m = m.plusMonths(1)) {
            List<Operation> list = ops.stream().filter(inMonths(m, m.plusMonths(1), zone)).toList();
            monthly.add(new MonthlyFlow(m, OperationTotals.accrued(list), OperationTotals.spent(list)));
        }

        // Неполные данные: в предыдущем периоде нет операций старше его первого месяца.
        boolean partial = ids.isEmpty() || !operationRepository.exists(
                employeeIn(ids).and(createdBefore(start(prevStart.plusMonths(1), zone))));

        int people = scope.size();
        return new Dashboard(period, curStart, prevStart,
                new Comparison(average(OperationTotals.accrued(cur), people), average(OperationTotals.accrued(prev), people)),
                new Comparison(conversion(cur, people), conversion(prev, people)),
                new Comparison(OperationTotals.accrued(cur), OperationTotals.accrued(prev)),
                new Comparison(OperationTotals.spent(cur), OperationTotals.spent(prev)),
                byDepartment, List.copyOf(monthly), partial);
    }

    /** Доля сотрудников, хотя бы раз потративших баллы на программы, %. */
    private static int conversion(List<Operation> ops, int people) {
        if (people == 0) {
            return 0;
        }
        long users = ops.stream()
                .filter(o -> o.getType() == OperationType.WRITEOFF && o.getStatus() == OperationStatus.WRITTEN_OFF)
                .map(o -> o.getAccount().getEmployee().getId())
                .distinct()
                .count();
        return (int) Math.round(users * 100.0 / people);
    }

    private static int average(int total, int people) {
        return people == 0 ? 0 : (int) Math.round((double) total / people);
    }

    private static Predicate<Operation> inMonths(YearMonth fromInclusive, YearMonth toExclusive, ZoneId zone) {
        var from = start(fromInclusive, zone);
        var to = start(toExclusive, zone);
        return o -> !o.getCreatedAt().isBefore(from) && o.getCreatedAt().isBefore(to);
    }

    private static Predicate<Operation> inDepartment(Department d) {
        return o -> d.getId().equals(o.getAccount().getEmployee().getDepartment().getId());
    }

    private static OffsetDateTime start(YearMonth month, ZoneId zone) {
        return BonusPeriods.startOfDay(month.atDay(1), zone);
    }
}
