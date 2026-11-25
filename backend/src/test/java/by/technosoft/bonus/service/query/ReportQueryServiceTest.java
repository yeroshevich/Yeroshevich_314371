package by.technosoft.bonus.service.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import by.technosoft.bonus.domain.Campaign;
import by.technosoft.bonus.domain.CampaignStatus;
import by.technosoft.bonus.domain.DemoData;
import by.technosoft.bonus.domain.Operation;
import by.technosoft.bonus.domain.OperationBasis;
import by.technosoft.bonus.domain.OperationStatus;
import by.technosoft.bonus.domain.OperationType;
import by.technosoft.bonus.domain.TestEntities;
import by.technosoft.bonus.repository.AmountByKey;
import by.technosoft.bonus.repository.BonusAccountRepository;
import by.technosoft.bonus.repository.CampaignRepository;
import by.technosoft.bonus.repository.DepartmentRepository;
import by.technosoft.bonus.repository.EmployeeRepository;
import by.technosoft.bonus.repository.OperationRepository;
import by.technosoft.bonus.service.exception.DomainValidationException;
import by.technosoft.bonus.service.query.ReportQueryService.Report;
import by.technosoft.bonus.service.query.ReportQueryService.ReportLine;

/** UC10 «Формирование отчётов»: начисления и списания за период по сотрудникам, подразделениям, кампаниям. */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReportQueryService — отчёты (UC10)")
class ReportQueryServiceTest {

    private static final LocalDate FROM = LocalDate.of(2026, 11, 1);
    private static final LocalDate TO = LocalDate.of(2026, 11, 30);

    @Mock private OperationRepository operationRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private BonusAccountRepository accountRepository;
    @Mock private CampaignRepository campaignRepository;

    private final DemoData d = new DemoData();
    private final Campaign autumn = TestEntities.campaign(1, "Осенний релиз 2.0", "2026-09-01", "2026-12-15", "1.50",
            60000, CampaignStatus.ACTIVE);
    private final Campaign noBudget = TestEntities.campaign(3, "Квартал без инцидентов", "2026-12-01", "2027-02-28",
            "2.00", null, CampaignStatus.NEEDS_APPROVAL);
    private ReportQueryService service;
    private List<Operation> november;

    @BeforeEach
    void setUp() {
        service = new ReportQueryService(operationRepository, employeeRepository, departmentRepository,
                accountRepository, campaignRepository, new CampaignQueryService(campaignRepository, operationRepository),
                TestEntities.clock());
        november = List.of(
                TestEntities.operation(146, d.annaAccount, OperationType.ACCRUAL, OperationStatus.PENDING,
                        OperationBasis.ACHIEVEMENT, 1500, TestEntities.at("2026-11-23", 10)),
                TestEntities.operation(140, d.annaAccount, OperationType.WRITEOFF, OperationStatus.WRITTEN_OFF,
                        OperationBasis.PROGRAM, -900, TestEntities.at("2026-11-21", 10)),
                TestEntities.operation(137, d.annaAccount, OperationType.ACCRUAL, OperationStatus.ACCRUED,
                        OperationBasis.KPI, 1700, TestEntities.at("2026-11-20", 10)).withCampaign(autumn),
                TestEntities.operation(130, d.maximAccount, OperationType.ACCRUAL, OperationStatus.ACCRUED,
                        OperationBasis.PROJECT, 500, TestEntities.at("2026-11-05", 10)));
        lenient().when(accountRepository.findAll())
                .thenReturn(List.of(d.annaAccount, d.maximAccount, d.pavelAccount));
        lenient().when(employeeRepository.findAllWithDepartment()).thenReturn(List.of(d.anna, d.maxim, d.pavel));
    }

    @SuppressWarnings("unchecked")
    private void operationsInPeriod(List<Operation> ops) {
        when(operationRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(ops);
    }

    @Test
    @DisplayName("FR10-1: отчёт по сотрудникам содержит начислено, потрачено, на согласовании и баланс")
    void employeeReport() {
        operationsInPeriod(november);

        List<ReportLine> rows = service.build(ReportType.EMPLOYEES, FROM, TO).rows();

        assertThat(rows).extracting(ReportLine::name).containsExactly("Анна Ковалёва", "Максим Новик");
        ReportLine anna = rows.get(0);
        assertThat(anna.group()).isEqualTo("Разработка ПО");
        assertThat(anna.accrued()).isEqualTo(1700);
        assertThat(anna.spent()).isEqualTo(900);
        assertThat(anna.pending()).isEqualTo(1500);
        assertThat(anna.balance()).isEqualTo(8900);
        assertThat(anna.operations()).isEqualTo(3);
    }

    @Test
    @DisplayName("FR10-1: отчёт по подразделениям суммирует начисления и балансы сотрудников подразделения")
    void departmentReport() {
        operationsInPeriod(november);
        when(departmentRepository.findAll(any(Sort.class))).thenReturn(List.of(d.development, d.qa));

        List<ReportLine> rows = service.build(ReportType.DEPARTMENTS, FROM, TO).rows();

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.name()).isEqualTo("Разработка ПО");
            assertThat(row.group()).isEqualTo("2 сотр.");
            assertThat(row.accrued()).isEqualTo(2200);
            assertThat(row.spent()).isEqualTo(900);
            assertThat(row.balance()).isEqualTo(8900 + 9950);
            assertThat(row.operations()).isEqualTo(4);
        });
    }

    @Test
    @DisplayName("FR10-1: отчёт по кампаниям показывает начисленное по кампании и остаток бюджета")
    void campaignReport() {
        operationsInPeriod(november);
        when(campaignRepository.findAllWithTargets()).thenReturn(List.of(noBudget, autumn));
        when(operationRepository.sumAmountGroupedByCampaign(any())).thenReturn(List.of(new AmountByKey(1L, 31_000L)));

        List<ReportLine> rows = service.build(ReportType.CAMPAIGNS, FROM, TO).rows();

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.name()).isEqualTo("Осенний релиз 2.0");
            assertThat(row.group()).isEqualTo("Бюджет 60\u00A0000\u00A0Б");
            assertThat(row.accrued()).isEqualTo(1700);
            assertThat(row.balance()).isEqualTo(29_000);
        });
    }

    @Test
    @DisplayName("FR10-2: период отчёта обязателен, начало не позже окончания, длина не более трёх лет")
    void periodIsValidated() {
        assertThatThrownBy(() -> service.build(ReportType.EMPLOYEES, null, TO))
                .isInstanceOf(DomainValidationException.class).hasMessage("Укажите период отчёта.");
        assertThatThrownBy(() -> service.build(ReportType.EMPLOYEES, TO, FROM))
                .isInstanceOf(DomainValidationException.class).hasMessageContaining("позже даты окончания");
        assertThatThrownBy(() -> service.build(ReportType.EMPLOYEES, LocalDate.of(2020, 1, 1), TO))
                .isInstanceOf(DomainValidationException.class).hasMessageContaining("трёх лет");
        verify(operationRepository, never()).findAll(any(Specification.class), any(Sort.class));
    }

    @Test
    @DisplayName("FR10-2: отчёт строится по операциям периода и сохраняет границы периода")
    void reportKeepsPeriod() {
        operationsInPeriod(november.subList(3, 4));

        Report report = service.build(ReportType.EMPLOYEES, LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 5));

        assertThat(report.from()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(report.to()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(report.rows()).extracting(ReportLine::key).containsExactly(3L);
    }

    @Test
    @DisplayName("FR10-3: пользователю предоставляется готовый отчёт с видом, периодом и временем формирования")
    void readyReportIsProvided() {
        operationsInPeriod(november);

        Report report = service.build(ReportType.EMPLOYEES, FROM, TO);

        assertThat(report.type()).isEqualTo(ReportType.EMPLOYEES);
        assertThat(report.from()).isEqualTo(FROM);
        assertThat(report.to()).isEqualTo(TO);
        assertThat(report.generatedAt()).isEqualTo(TestEntities.now());
        assertThat(report.rows()).isNotEmpty();
    }

    @Test
    @DisplayName("FR10-3: за период без операций формируется пустой отчёт (без ошибки)")
    void emptyPeriodGivesEmptyReport() {
        operationsInPeriod(List.of());

        Report report = service.build(ReportType.EMPLOYEES, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));

        assertThat(report.rows()).isEmpty();
        assertThat(report.generatedAt()).isNotNull();
    }
}
