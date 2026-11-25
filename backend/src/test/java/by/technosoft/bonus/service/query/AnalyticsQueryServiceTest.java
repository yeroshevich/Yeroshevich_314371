package by.technosoft.bonus.service.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import by.technosoft.bonus.domain.AppUser;
import by.technosoft.bonus.domain.BonusAccount;
import by.technosoft.bonus.domain.DemoData;
import by.technosoft.bonus.domain.Operation;
import by.technosoft.bonus.domain.OperationBasis;
import by.technosoft.bonus.domain.OperationStatus;
import by.technosoft.bonus.domain.OperationType;
import by.technosoft.bonus.domain.TestEntities;
import by.technosoft.bonus.repository.AppUserRepository;
import by.technosoft.bonus.repository.DepartmentRepository;
import by.technosoft.bonus.repository.EmployeeRepository;
import by.technosoft.bonus.repository.OperationRepository;
import by.technosoft.bonus.service.AccessPolicy;
import by.technosoft.bonus.service.exception.AccessDeniedDomainException;
import by.technosoft.bonus.service.query.AnalyticsQueryService.Comparison;
import by.technosoft.bonus.service.query.AnalyticsQueryService.Dashboard;
import by.technosoft.bonus.service.query.AnalyticsQueryService.DepartmentComparison;
import by.technosoft.bonus.service.query.AnalyticsQueryService.MonthlyFlow;

/** UC11 «Анализ эффективности»: показатели, метрики сравнения периодов и подразделений, видимость результатов. */
@ExtendWith(MockitoExtension.class)
@DisplayName("AnalyticsQueryService — анализ эффективности (UC11)")
class AnalyticsQueryServiceTest {

    @Mock private AppUserRepository userRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private OperationRepository operationRepository;

    private final DemoData d = new DemoData();
    private AnalyticsQueryService service;

    @BeforeEach
    void setUp() {
        service = new AnalyticsQueryService(new AccessPolicy(userRepository), employeeRepository, departmentRepository,
                operationRepository, TestEntities.clock());
        for (AppUser u : new AppUser[] {d.employeeUser, d.managerUser, d.hrUser}) {
            lenient().when(userRepository.findWithRoleById(u.getId())).thenReturn(Optional.of(u));
        }
        lenient().when(departmentRepository.findAll(any(Sort.class))).thenReturn(List.of(d.development, d.qa));
    }

    private static Operation op(long id, BonusAccount account, OperationType type, int amount, String date) {
        return TestEntities.operation(id, account, type,
                type == OperationType.WRITEOFF ? OperationStatus.WRITTEN_OFF : OperationStatus.ACCRUED,
                type == OperationType.WRITEOFF ? OperationBasis.PROGRAM : OperationBasis.KPI, amount,
                TestEntities.at(date, 10));
    }

    /** Ноябрь (текущий месяц) и октябрь (предыдущий) для трёх сотрудников, плюс июнь для динамики. */
    @SuppressWarnings("unchecked")
    private void companyOperations() {
        when(employeeRepository.findAll()).thenReturn(List.of(d.anna, d.maxim, d.pavel));
        when(operationRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(
                op(10, d.annaAccount, OperationType.ACCRUAL, 1700, "2026-11-20"),
                op(11, d.maximAccount, OperationType.ACCRUAL, 500, "2026-11-05"),
                op(12, d.annaAccount, OperationType.WRITEOFF, -900, "2026-11-03"),
                op(13, d.annaAccount, OperationType.ACCRUAL, 1200, "2026-10-20"),
                op(14, d.pavelAccount, OperationType.ACCRUAL, 300, "2026-10-10"),
                op(15, d.maximAccount, OperationType.WRITEOFF, -1500, "2026-10-02"),
                op(16, d.annaAccount, OperationType.ACCRUAL, 1000, "2026-06-15")));
        when(operationRepository.exists(any(Specification.class))).thenReturn(true);
    }

    @Test
    @DisplayName("FR11-1: рассчитываются показатели эффективности — средний бонус и конверсия программ")
    void calculatesEfficiencyIndicators() {
        companyOperations();

        Dashboard dashboard = service.dashboard(20L, AnalyticsPeriod.MONTH);

        assertThat(dashboard.averageBonus()).isEqualTo(new Comparison(733, 500)); // 2200 / 3 и 1500 / 3
        assertThat(dashboard.programConversion()).isEqualTo(new Comparison(33, 33)); // 1 из 3 сотрудников
        assertThat(dashboard.totalAccrued()).isEqualTo(new Comparison(2200, 1500));
        assertThat(dashboard.totalSpent()).isEqualTo(new Comparison(900, 1500));
    }

    @Test
    @DisplayName("FR11-2: формируются метрики сравнения по подразделениям и динамика за 6 месяцев")
    void buildsComparisonMetrics() {
        companyOperations();

        Dashboard dashboard = service.dashboard(20L, AnalyticsPeriod.MONTH);

        assertThat(dashboard.currentStart()).isEqualTo(YearMonth.of(2026, 11));
        assertThat(dashboard.previousStart()).isEqualTo(YearMonth.of(2026, 10));
        assertThat(dashboard.byDepartment()).containsExactly(
                new DepartmentComparison("Разработка ПО", 2200, 1200),
                new DepartmentComparison("Обеспечение качества", 0, 300));
        assertThat(dashboard.monthly()).extracting(MonthlyFlow::month)
                .containsExactly(YearMonth.of(2026, 6), YearMonth.of(2026, 7), YearMonth.of(2026, 8),
                        YearMonth.of(2026, 9), YearMonth.of(2026, 10), YearMonth.of(2026, 11));
        assertThat(dashboard.monthly().get(0)).isEqualTo(new MonthlyFlow(YearMonth.of(2026, 6), 1000, 0));
        assertThat(dashboard.monthly().get(5)).isEqualTo(new MonthlyFlow(YearMonth.of(2026, 11), 2200, 900));
        assertThat(dashboard.partial()).isFalse();
    }

    @Test
    @DisplayName("FR11-2: период «квартал» сравнивается с предыдущим кварталом той же длины")
    void quarterComparison() {
        companyOperations();

        Dashboard dashboard = service.dashboard(20L, AnalyticsPeriod.QUARTER);

        assertThat(dashboard.currentStart()).isEqualTo(YearMonth.of(2026, 9));
        assertThat(dashboard.previousStart()).isEqualTo(YearMonth.of(2026, 6));
        assertThat(dashboard.totalAccrued()).isEqualTo(new Comparison(3700, 1000));
    }

    @Test
    @DisplayName("FR11-2: при нехватке истории для сравнения дашборд помечается как неполный")
    @SuppressWarnings("unchecked")
    void partialDataIsFlagged() {
        when(employeeRepository.findAll()).thenReturn(List.of(d.anna));
        when(operationRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());
        when(operationRepository.exists(any(Specification.class))).thenReturn(false);

        Dashboard dashboard = service.dashboard(20L, AnalyticsPeriod.HALFYEAR);

        assertThat(dashboard.partial()).isTrue();
        assertThat(dashboard.averageBonus()).isEqualTo(new Comparison(0, 0));
    }

    @Test
    @DisplayName("FR11-3: результаты анализа отображаются руководителю только по его подразделению")
    @SuppressWarnings("unchecked")
    void managerSeesOwnDepartment() {
        when(employeeRepository.findByDepartmentId(1L)).thenReturn(List.of(d.dmitry, d.anna, d.maxim));
        when(operationRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(
                op(10, d.annaAccount, OperationType.ACCRUAL, 1700, "2026-11-20")));
        when(operationRepository.exists(any(Specification.class))).thenReturn(true);

        Dashboard dashboard = service.dashboard(2L, AnalyticsPeriod.MONTH);

        assertThat(dashboard.byDepartment()).extracting(DepartmentComparison::department)
                .containsExactly("Разработка ПО");
        assertThat(dashboard.averageBonus().current()).isEqualTo(567); // 1700 / 3
        verify(employeeRepository, never()).findAll();
    }

    @Test
    @DisplayName("FR11-3: результаты анализа доступны только HR и руководителю")
    void employeeCannotSeeAnalytics() {
        assertThatThrownBy(() -> service.dashboard(1L, AnalyticsPeriod.MONTH))
                .isInstanceOf(AccessDeniedDomainException.class);
    }

    @Test
    @DisplayName("FR11-1: при отсутствии сотрудников в области анализа показатели равны нулю, данные неполные")
    void emptyScope() {
        when(employeeRepository.findByDepartmentId(1L)).thenReturn(List.of());

        Dashboard dashboard = service.dashboard(2L, AnalyticsPeriod.MONTH);

        assertThat(dashboard.programConversion()).isEqualTo(new Comparison(0, 0));
        assertThat(dashboard.partial()).isTrue();
        verify(operationRepository, never()).findAll(any(Specification.class), any(Sort.class));
    }
}
