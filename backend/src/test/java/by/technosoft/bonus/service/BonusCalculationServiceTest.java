package by.technosoft.bonus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import by.technosoft.bonus.domain.AccrualRule;
import by.technosoft.bonus.domain.Campaign;
import by.technosoft.bonus.domain.CampaignStatus;
import by.technosoft.bonus.domain.DemoData;
import by.technosoft.bonus.domain.RulePeriodicity;
import by.technosoft.bonus.domain.TestEntities;
import by.technosoft.bonus.repository.AccrualRuleRepository;
import by.technosoft.bonus.repository.CampaignDepartmentRepository;
import by.technosoft.bonus.repository.CampaignRepository;
import by.technosoft.bonus.repository.EmployeeRepository;
import by.technosoft.bonus.repository.OperationRepository;
import by.technosoft.bonus.service.dto.CalculationRequest;
import by.technosoft.bonus.service.dto.CalculationResult;
import by.technosoft.bonus.service.exception.InvalidCalculationInputException;
import by.technosoft.bonus.service.strategy.AccrualStrategyRegistry;
import by.technosoft.bonus.service.strategy.FixedEventAccrualStrategy;
import by.technosoft.bonus.service.strategy.KpiAccrualStrategy;

/**
 * UC6 «Расчёт бонусов» (алгоритм А1): валидация входных данных, правило, KPI-коэффициенты,
 * коэффициент кампании с учётом бюджета, округление. Репозитории — заглушки Mockito.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BonusCalculationService — расчёт бонусов (UC6)")
class BonusCalculationServiceTest {

    private static final LocalDate NOV_20 = LocalDate.of(2026, 11, 20);

    @Mock private EmployeeRepository employeeRepository;
    @Mock private AccrualRuleRepository ruleRepository;
    @Mock private CampaignRepository campaignRepository;
    @Mock private CampaignDepartmentRepository campaignDepartmentRepository;
    @Mock private OperationRepository operationRepository;

    private final DemoData d = new DemoData();
    private final AccrualRule sprint = TestEntities.sprintRule();
    private final Campaign autumn = TestEntities.campaign(1, "Осенний релиз 2.0", "2026-09-01", "2026-12-15", "1.50",
            60000, CampaignStatus.ACTIVE);
    private BonusCalculationService service;

    @BeforeEach
    void setUp() {
        service = new BonusCalculationService(employeeRepository, ruleRepository, campaignRepository,
                campaignDepartmentRepository, operationRepository,
                new AccrualStrategyRegistry(List.of(new KpiAccrualStrategy(), new FixedEventAccrualStrategy())),
                new ObjectMapper());
    }

    private void employeeAndRule() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(d.anna));
        when(ruleRepository.findById(1L)).thenReturn(Optional.of(sprint));
    }

    private void campaigns(Campaign... campaigns) {
        when(campaignRepository.findRunningForDepartment(eq(1L), eq(CampaignStatus.ACTIVE), any()))
                .thenReturn(List.of(campaigns));
    }

    // ── FR6-1: расчёт выполняется при наличии входных данных ─────────────────────────────

    @Test
    @DisplayName("FR6-1: при наличии сотрудника, правила, KPI и даты система выполняет расчёт (ОП-000001: 1392 → 1400)")
    void calculatesWhenInputPresent() {
        employeeAndRule();
        campaigns();

        CalculationResult result = service.calculate(
                new CalculationRequest(1L, 1L, new BigDecimal("116"), LocalDate.of(2026, 6, 25)));

        assertThat(result.employeeId()).isEqualTo(1L);
        assertThat(result.ruleId()).isEqualTo(1L);
        assertThat(result.baseAmount()).isEqualByComparingTo("1392");
        assertThat(result.coefficient()).isEqualByComparingTo("1");
        assertThat(result.campaignId()).isNull();
        assertThat(result.total()).isEqualTo(1400);
    }

    static Stream<Arguments> incompleteRequests() {
        BigDecimal kpi = BigDecimal.TEN;
        return Stream.of(
                Arguments.of("запрос отсутствует", null),
                Arguments.of("нет сотрудника", new CalculationRequest(null, 1L, kpi, NOV_20)),
                Arguments.of("нет правила", new CalculationRequest(1L, null, kpi, NOV_20)),
                Arguments.of("нет даты", new CalculationRequest(1L, 1L, kpi, null)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("incompleteRequests")
    @DisplayName("FR6-1: без обязательных входных данных расчёт не выполняется")
    void rejectsIncompleteInput(String caseName, CalculationRequest request) {
        assertThatThrownBy(() -> service.calculate(request))
                .isInstanceOf(InvalidCalculationInputException.class)
                .hasMessageContaining("не указаны сотрудник, правило или дата");
        verifyNoInteractions(employeeRepository, ruleRepository, campaignRepository);
    }

    @Test
    @DisplayName("FR6-1: неизвестный сотрудник или правило — некорректные входные данные")
    void rejectsUnknownEmployeeOrRule() {
        when(employeeRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.calculate(new CalculationRequest(99L, 1L, BigDecimal.TEN, NOV_20)))
                .isInstanceOf(InvalidCalculationInputException.class)
                .hasMessageContaining("сотрудник 99 не найден");

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(d.anna));
        when(ruleRepository.findById(77L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.calculate(new CalculationRequest(1L, 77L, BigDecimal.TEN, NOV_20)))
                .isInstanceOf(InvalidCalculationInputException.class)
                .hasMessageContaining("правило 77 не найдено");
    }

    // ── FR6-2: учёт KPI-коэффициентов правила ─────────────────────────────────────────

    @Test
    @DisplayName("FR6-2: итог учитывает вес показателя и уровень достижения KPI (KPI 91 %: 3000 × 0,4 × 0,91 = 1092 → 1100)")
    void appliesKpiCoefficients() {
        employeeAndRule();
        campaigns();

        CalculationResult result = service.calculate(new CalculationRequest(1L, 1L, new BigDecimal("91"), NOV_20));

        assertThat(result.baseAmount()).isEqualByComparingTo("1092");
        assertThat(result.total()).isEqualTo(1100);
        assertThat(result.formula()).contains("3000 × 0.40 × 91 %").endsWith("округление до 50 → 1100");
    }

    @Test
    @DisplayName("FR6-2: KPI ниже порога — итог 0, кампании не запрашиваются (BR-5)")
    void belowThresholdIsZero() {
        employeeAndRule();

        CalculationResult result = service.calculate(new CalculationRequest(1L, 1L, new BigDecimal("79.99"), NOV_20));

        assertThat(result.isZero()).isTrue();
        assertThat(result.total()).isZero();
        assertThat(result.coefficient()).isEqualByComparingTo("1");
        verifyNoInteractions(campaignRepository);
    }

    // ── FR8-3: при расчёте применяются только активные действующие правила ───────────────

    @Test
    @DisplayName("FR8-3: неактивное правило при расчёте не применяется")
    void inactiveRuleIsNotApplied() {
        AccrualRule cert = TestEntities.rule(6, "Профессиональная сертификация", TestEntities.kpi(6, "CERT", "Сертификат"),
                "0.25", 8000, RulePeriodicity.EVENT, "100", "{}", false, LocalDate.of(2026, 6, 1));
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(d.anna));
        when(ruleRepository.findById(6L)).thenReturn(Optional.of(cert));

        assertThatThrownBy(() -> service.calculate(new CalculationRequest(1L, 6L, null, NOV_20)))
                .isInstanceOf(InvalidCalculationInputException.class)
                .hasMessageContaining("не действует на 2026-11-20");
    }

    @Test
    @DisplayName("FR8-3: версия правила с датой вступления в силу позже даты расчёта не применяется")
    void futureRuleVersionIsNotApplied() {
        AccrualRule december = TestEntities.rule(7, "Спринт (новая редакция)", sprint.getKpi(), "0.50", 3000,
                RulePeriodicity.MONTH, "80", "{}", true, LocalDate.of(2026, 12, 1));
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(d.anna));
        when(ruleRepository.findById(7L)).thenReturn(Optional.of(december));

        assertThatThrownBy(() -> service.calculate(new CalculationRequest(1L, 7L, BigDecimal.TEN, NOV_20)))
                .isInstanceOf(InvalidCalculationInputException.class);
        assertThat(december.isApplicableOn(LocalDate.of(2026, 12, 1))).isTrue();
    }

    // ── FR9-2: коэффициент активной кампании ─────────────────────────────────────────

    @Test
    @DisplayName("FR9-2: активная кампания подразделения применяет свой коэффициент (ОП-000144: 1092 × 1,5 = 1638 → 1650)")
    void appliesActiveCampaignCoefficient() {
        employeeAndRule();
        campaigns(autumn);
        when(operationRepository.sumAmountByCampaign(eq(1L), any())).thenReturn(31_000L);

        CalculationResult result = service.calculate(new CalculationRequest(1L, 1L, new BigDecimal("91"), NOV_20));

        assertThat(result.coefficient()).isEqualByComparingTo("1.5");
        assertThat(result.campaignId()).isEqualTo(1L);
        assertThat(result.total()).isEqualTo(1650);
        assertThat(result.formula()).contains("× 1.50 (кампания «Осенний релиз 2.0»)");
    }

    @Test
    @DisplayName("FR9-2: кампания с исчерпанным бюджетом пропускается — применяется следующая подходящая")
    void skipsCampaignWithoutBudgetLeft() {
        Campaign rich = TestEntities.campaign(5, "Двойные баллы", "2026-11-01", "2026-11-30", "2.00", 1000,
                CampaignStatus.ACTIVE);
        employeeAndRule();
        campaigns(rich, autumn);
        when(operationRepository.sumAmountByCampaign(eq(5L), any())).thenReturn(900L); // 900 + 2200 > 1000
        when(operationRepository.sumAmountByCampaign(eq(1L), any())).thenReturn(0L);

        CalculationResult result = service.calculate(new CalculationRequest(1L, 1L, new BigDecimal("91"), NOV_20));

        assertThat(result.campaignId()).isEqualTo(1L);
        assertThat(result.total()).isEqualTo(1650);
    }

    @Test
    @DisplayName("FR9-2: если бюджет ни одной кампании не позволяет начисление — коэффициент 1")
    void noCampaignFitsBudget() {
        Campaign small = TestEntities.campaign(5, "Малый бюджет", "2026-11-01", "2026-11-30", "2.00", 100,
                CampaignStatus.ACTIVE);
        employeeAndRule();
        campaigns(small);
        when(operationRepository.sumAmountByCampaign(eq(5L), any())).thenReturn(0L);

        CalculationResult result = service.calculate(new CalculationRequest(1L, 1L, new BigDecimal("91"), NOV_20));

        assertThat(result.campaignId()).isNull();
        assertThat(result.coefficient()).isEqualByComparingTo("1");
        assertThat(result.total()).isEqualTo(1100);
    }

    @Test
    @DisplayName("FR9-2: кампания без ограничения бюджета применяется без проверки израсходованного")
    void unlimitedCampaignDoesNotCheckSpent() {
        Campaign unlimited = TestEntities.campaign(6, "Без лимита", "2026-11-01", "2026-11-30", "1.20", null,
                CampaignStatus.ACTIVE);
        employeeAndRule();
        campaigns(unlimited);

        CalculationResult result = service.calculate(new CalculationRequest(1L, 1L, new BigDecimal("100"), NOV_20));

        assertThat(result.total()).isEqualTo(1450); // 1200 × 1,2 = 1440 → 1450
        verify(operationRepository, never()).sumAmountByCampaign(anyLong(), any());
    }

    // ── FR6-3: возврат итоговой суммы ──────────────────────────────────────────────

    @Test
    @DisplayName("FR6-3: итоговая сумма округляется до шага roundTo из параметров правила")
    void roundsToRuleStep() {
        AccrualRule byHundred = TestEntities.rule(1, "Спринт", sprint.getKpi(), "0.40", 3000, RulePeriodicity.MONTH,
                "80", "{\"roundTo\": 100}", true, LocalDate.of(2026, 6, 1));
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(d.anna));
        when(ruleRepository.findById(1L)).thenReturn(Optional.of(byHundred));
        campaigns();

        CalculationResult result = service.calculate(new CalculationRequest(1L, 1L, new BigDecimal("104"), NOV_20));

        assertThat(result.baseAmount()).isEqualByComparingTo("1248");
        assertThat(result.total()).isEqualTo(1200);
    }

    @Test
    @DisplayName("FR6-3: без roundTo в параметрах итог округляется до 50 баллов")
    void defaultRoundingIsFifty() {
        AccrualRule noParams = TestEntities.rule(1, "Спринт", sprint.getKpi(), "0.40", 3000, RulePeriodicity.MONTH,
                "80", "{}", true, LocalDate.of(2026, 6, 1));
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(d.anna));
        when(ruleRepository.findById(1L)).thenReturn(Optional.of(noParams));
        campaigns();

        assertThat(service.calculate(new CalculationRequest(1L, 1L, new BigDecimal("104"), NOV_20)).total())
                .isEqualTo(1250);
    }

    @Test
    @DisplayName("FR6-3: повреждённые параметры правила — итог не возвращается, ошибка входных данных")
    void brokenParamsAreReported() {
        AccrualRule broken = TestEntities.rule(1, "Спринт", sprint.getKpi(), "0.40", 3000, RulePeriodicity.MONTH,
                "80", "{roundTo", true, LocalDate.of(2026, 6, 1));
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(d.anna));
        when(ruleRepository.findById(1L)).thenReturn(Optional.of(broken));

        assertThatThrownBy(() -> service.calculate(new CalculationRequest(1L, 1L, new BigDecimal("100"), NOV_20)))
                .isInstanceOf(InvalidCalculationInputException.class)
                .hasMessageContaining("params не является JSON");
    }

    @Test
    @DisplayName("FR6-3: округление до ближайшего кратного шагу, половина — вверх")
    void roundToStepHalfUp() {
        assertThat(BonusCalculationService.roundToStep(new BigDecimal("1375"), 50)).isEqualTo(1400);
        assertThat(BonusCalculationService.roundToStep(new BigDecimal("1374.99"), 50)).isEqualTo(1350);
        assertThat(BonusCalculationService.roundToStep(new BigDecimal("1638"), 50)).isEqualTo(1650);
        assertThat(BonusCalculationService.roundToStep(new BigDecimal("1200.5"), 1)).isEqualTo(1201);
    }

    // ── Ручная сумма (UC5 + UC6) ─────────────────────────────────────────────────

    @Test
    @DisplayName("FR9-2: ручное начисление в активную кампанию подразделения умножается на её коэффициент (600 × 1,5 = 900)")
    void manualAmountWithCampaign() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(d.anna));
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(autumn));
        when(campaignDepartmentRepository.existsByCampaignIdAndDepartmentId(1L, 1L)).thenReturn(true);
        when(operationRepository.sumAmountByCampaign(eq(1L), any())).thenReturn(0L);

        CalculationResult result = service.calculateManual(1L, 600, 1L, NOV_20);

        assertThat(result.total()).isEqualTo(900);
        assertThat(result.campaignId()).isEqualTo(1L);
        assertThat(result.formula()).isEqualTo("600 × 1.50 (кампания «Осенний релиз 2.0») = 900");
    }

    @Test
    @DisplayName("FR9-2: кампания, не охватывающая подразделение сотрудника, не применяется")
    void manualAmountCampaignNotTargeted() {
        when(employeeRepository.findById(9L)).thenReturn(Optional.of(d.pavel));
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(autumn));
        when(campaignDepartmentRepository.existsByCampaignIdAndDepartmentId(1L, 2L)).thenReturn(false);

        CalculationResult result = service.calculateManual(9L, 600, 1L, NOV_20);

        assertThat(result.total()).isEqualTo(600);
        assertThat(result.campaignId()).isNull();
    }

    @Test
    @DisplayName("FR9-3: после даты окончания кампания не применяется — коэффициент 1")
    void campaignIsInactiveAfterEnd() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(d.anna));
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(autumn));
        when(campaignDepartmentRepository.existsByCampaignIdAndDepartmentId(1L, 1L)).thenReturn(true);

        CalculationResult lastDay = service.calculateManual(1L, 600, 1L, LocalDate.of(2026, 12, 15));
        CalculationResult afterEnd = service.calculateManual(1L, 600, 1L, LocalDate.of(2026, 12, 16));

        assertThat(lastDay.total()).isEqualTo(900);
        assertThat(afterEnd.total()).isEqualTo(600);
        assertThat(afterEnd.coefficient()).isEqualByComparingTo("1");
        assertThat(afterEnd.campaignId()).isNull();
    }

    @Test
    @DisplayName("FR9-3: завершённая кампания не применяется даже в пределах своих дат")
    void finishedCampaignIsNotApplied() {
        Campaign hackathon = TestEntities.campaign(4, "Летний хакатон", "2026-07-01", "2026-07-31", "1.20", 40000,
                CampaignStatus.FINISHED);
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(d.anna));
        when(campaignRepository.findById(4L)).thenReturn(Optional.of(hackathon));
        when(campaignDepartmentRepository.existsByCampaignIdAndDepartmentId(4L, 1L)).thenReturn(true);

        assertThat(service.calculateManual(1L, 500, 4L, LocalDate.of(2026, 7, 15)).total()).isEqualTo(500);
    }

    @Test
    @DisplayName("FR6-1: ручной расчёт с нулевой суммой или неизвестной кампанией не выполняется")
    void manualRejectsInvalidInput() {
        assertThatThrownBy(() -> service.calculateManual(1L, 0, null, NOV_20))
                .isInstanceOf(InvalidCalculationInputException.class)
                .hasMessageContaining("сумма должна быть больше нуля");

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(d.anna));
        when(campaignRepository.findById(42L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.calculateManual(1L, 100, 42L, NOV_20))
                .isInstanceOf(InvalidCalculationInputException.class)
                .hasMessageContaining("кампания 42 не найдена");
    }
}
