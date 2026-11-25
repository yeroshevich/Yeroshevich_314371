package by.technosoft.bonus.service.query;

import static by.technosoft.bonus.repository.OperationSpecifications.createdBefore;
import static by.technosoft.bonus.repository.OperationSpecifications.createdFrom;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import by.technosoft.bonus.domain.BonusAccount;
import by.technosoft.bonus.domain.Campaign;
import by.technosoft.bonus.domain.Department;
import by.technosoft.bonus.domain.Employee;
import by.technosoft.bonus.domain.Operation;
import by.technosoft.bonus.repository.BonusAccountRepository;
import by.technosoft.bonus.repository.CampaignRepository;
import by.technosoft.bonus.repository.DepartmentRepository;
import by.technosoft.bonus.repository.EmployeeRepository;
import by.technosoft.bonus.repository.OperationRepository;
import by.technosoft.bonus.service.PointsFormat;
import by.technosoft.bonus.service.exception.DomainValidationException;

/**
 * Отчёты по начислениям и списаниям за период (UC10): по сотрудникам, подразделениям, кампаниям.
 * В отчёт попадают только строки, по которым в периоде были операции. Экспорт в Excel/PDF — на клиенте.
 */
@Service
@Transactional(readOnly = true)
public class ReportQueryService {

    /** Максимальная длина периода отчёта, дней (защита от выборки всей истории). */
    static final int MAX_PERIOD_DAYS = 3 * 366;

    /**
     * Строка отчёта.
     *
     * @param balance текущий баланс (сотрудник, подразделение) или остаток бюджета (кампания); {@code null} — нет
     */
    public record ReportLine(Long key, String name, String group, int accrued, int spent, int pending,
                             Integer balance, int operations) {
    }

    public record Report(ReportType type, LocalDate from, LocalDate to, List<ReportLine> rows,
                         OffsetDateTime generatedAt) {
    }

    private final OperationRepository operationRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final BonusAccountRepository accountRepository;
    private final CampaignRepository campaignRepository;
    private final CampaignQueryService campaignQueryService;
    private final Clock clock;

    public ReportQueryService(OperationRepository operationRepository,
                              EmployeeRepository employeeRepository,
                              DepartmentRepository departmentRepository,
                              BonusAccountRepository accountRepository,
                              CampaignRepository campaignRepository,
                              CampaignQueryService campaignQueryService,
                              Clock clock) {
        this.operationRepository = operationRepository;
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.accountRepository = accountRepository;
        this.campaignRepository = campaignRepository;
        this.campaignQueryService = campaignQueryService;
        this.clock = clock;
    }

    /** Формирует отчёт за период [from; to] (даты включительно). */
    public Report build(ReportType type, LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new DomainValidationException("Укажите период отчёта.");
        }
        if (from.isAfter(to)) {
            throw new DomainValidationException("Дата начала периода позже даты окончания. Проверьте период.");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_PERIOD_DAYS) {
            throw new DomainValidationException("Период отчёта не может быть длиннее трёх лет.");
        }
        ZoneId zone = clock.getZone();
        List<Operation> ops = operationRepository.findAll(
                createdFrom(BonusPeriods.startOfDay(from, zone))
                        .and(createdBefore(BonusPeriods.startOfDay(to.plusDays(1), zone))),
                AccountQueryService.NEWEST_FIRST);
        List<ReportLine> rows = switch (type) {
            case EMPLOYEES -> byEmployee(ops);
            case DEPARTMENTS -> byDepartment(ops);
            case CAMPAIGNS -> byCampaign(ops);
        };
        return new Report(type, from, to, rows.stream().filter(r -> r.operations() > 0).toList(),
                OffsetDateTime.now(clock));
    }

    private List<ReportLine> byEmployee(List<Operation> ops) {
        Map<Long, List<Operation>> byEmployee = ops.stream()
                .collect(Collectors.groupingBy(o -> o.getAccount().getEmployee().getId()));
        Map<Long, Integer> balances = balances();
        return employeeRepository.findAllWithDepartment().stream()
                .map(e -> line(e.getId(), e.getFullName(), e.getDepartment().getName(),
                        byEmployee.getOrDefault(e.getId(), List.of()), balances.getOrDefault(e.getId(), 0)))
                .toList();
    }

    private List<ReportLine> byDepartment(List<Operation> ops) {
        List<Employee> employees = employeeRepository.findAllWithDepartment();
        Map<Long, Integer> balances = balances();
        Map<Long, List<Employee>> staff = employees.stream()
                .collect(Collectors.groupingBy(e -> e.getDepartment().getId()));
        Map<Long, List<Operation>> byDepartment = ops.stream()
                .collect(Collectors.groupingBy(o -> o.getAccount().getEmployee().getDepartment().getId()));
        return departmentRepository.findAll(Sort.by("id")).stream()
                .map((Department d) -> {
                    List<Employee> people = staff.getOrDefault(d.getId(), List.of());
                    int balance = people.stream().mapToInt(e -> balances.getOrDefault(e.getId(), 0)).sum();
                    return line(d.getId(), d.getName(), people.size() + " сотр.",
                            byDepartment.getOrDefault(d.getId(), List.of()), balance);
                })
                .toList();
    }

    private List<ReportLine> byCampaign(List<Operation> ops) {
        Map<Long, List<Operation>> byCampaign = ops.stream()
                .filter(o -> o.getCampaign() != null)
                .collect(Collectors.groupingBy(o -> o.getCampaign().getId()));
        Map<Long, Long> spent = campaignQueryService.spentByCampaign();
        return campaignRepository.findAllWithTargets().stream()
                .map((Campaign c) -> {
                    List<Operation> list = byCampaign.getOrDefault(c.getId(), List.of());
                    Integer rest = c.getBudget() == null ? null
                            : c.getBudget() - spent.getOrDefault(c.getId(), 0L).intValue();
                    String group = c.getBudget() == null ? "Бюджет не выделен"
                            : "Бюджет " + PointsFormat.format(c.getBudget());
                    return new ReportLine(c.getId(), c.getName(), group, OperationTotals.accrued(list), 0,
                            OperationTotals.pending(list), rest, list.size());
                })
                .toList();
    }

    private static ReportLine line(Long key, String name, String group, List<Operation> ops, Integer balance) {
        return new ReportLine(key, name, group, OperationTotals.accrued(ops), OperationTotals.spent(ops),
                OperationTotals.pending(ops), balance, ops.size());
    }

    /** Текущие балансы: сотрудник → баланс. */
    private Map<Long, Integer> balances() {
        return accountRepository.findAll().stream()
                .collect(Collectors.toMap(a -> a.getEmployee().getId(), BonusAccount::getBalance,
                        (a, b) -> a, HashMap::new));
    }
}
