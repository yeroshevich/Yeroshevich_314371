package by.technosoft.bonus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import com.fasterxml.jackson.databind.ObjectMapper;

import by.technosoft.bonus.config.BonusProperties;
import by.technosoft.bonus.domain.AccrualRule;
import by.technosoft.bonus.domain.BusinessEvent;
import by.technosoft.bonus.domain.Campaign;
import by.technosoft.bonus.domain.CampaignStatus;
import by.technosoft.bonus.domain.DemoData;
import by.technosoft.bonus.domain.EventStatus;
import by.technosoft.bonus.domain.Integration;
import by.technosoft.bonus.domain.IntegrationKind;
import by.technosoft.bonus.domain.Operation;
import by.technosoft.bonus.domain.OperationBasis;
import by.technosoft.bonus.domain.OperationStatus;
import by.technosoft.bonus.domain.OperationType;
import by.technosoft.bonus.domain.RulePeriodicity;
import by.technosoft.bonus.domain.TestEntities;
import by.technosoft.bonus.repository.AccrualRuleRepository;
import by.technosoft.bonus.repository.BonusAccountRepository;
import by.technosoft.bonus.repository.BusinessEventRepository;
import by.technosoft.bonus.repository.CampaignDepartmentRepository;
import by.technosoft.bonus.repository.CampaignRepository;
import by.technosoft.bonus.repository.EmployeeRepository;
import by.technosoft.bonus.repository.OperationRepository;
import by.technosoft.bonus.service.dto.BatchProcessingResult;
import by.technosoft.bonus.service.dto.EventOutcome;
import by.technosoft.bonus.service.dto.EventProcessingResult;
import by.technosoft.bonus.service.event.EventProcessingFailedEvent;
import by.technosoft.bonus.service.event.OperationAction;
import by.technosoft.bonus.service.event.OperationRegisteredEvent;
import by.technosoft.bonus.service.exception.ObjectNotFoundException;
import by.technosoft.bonus.service.strategy.AccrualStrategyRegistry;
import by.technosoft.bonus.service.strategy.FixedEventAccrualStrategy;
import by.technosoft.bonus.service.strategy.KpiAccrualStrategy;

/**
 * UC4 «Автоматическое начисление» (алгоритм А2) и обработка внешних событий интеграций (UC13).
 * Расчёт выполняет настоящий BonusCalculationService (UC6) поверх заглушек репозиториев.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EventProcessingService — автоначисление по событиям (UC4, UC13)")
class EventProcessingServiceTest {

    private static final OffsetDateTime RECEIVED = OffsetDateTime.parse("2026-11-24T12:00:00+03:00");

    @Mock private BusinessEventRepository eventRepository;
    @Mock private OperationRepository operationRepository;
    @Mock private AccrualRuleRepository ruleRepository;
    @Mock private BonusAccountRepository accountRepository;
    @Mock private CampaignRepository campaignRepository;
    @Mock private CampaignDepartmentRepository campaignDepartmentRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private ApplicationEventPublisher events;
    @Mock private PlatformTransactionManager transactionManager;

    private final DemoData d = new DemoData();
    private final AccrualRule sprint = TestEntities.sprintRule();
    private final Campaign autumn = TestEntities.campaign(1, "Осенний релиз 2.0", "2026-09-01", "2026-12-15", "1.50",
            60000, CampaignStatus.ACTIVE);
    private final Integration hris = TestEntities.integration(1, IntegrationKind.HRIS, "HRIS",
            "https://hris.technosoft.by/api/v2", "enc", true);
    private final Integration erp = TestEntities.integration(2, IntegrationKind.ERP, "ERP",
            "https://erp.technosoft.by/api", "enc", true);
    private EventProcessingService service;

    @BeforeEach
    void setUp() {
        BonusCalculationService calculation = new BonusCalculationService(employeeRepository, ruleRepository,
                campaignRepository, campaignDepartmentRepository, operationRepository,
                new AccrualStrategyRegistry(List.of(new KpiAccrualStrategy(), new FixedEventAccrualStrategy())),
                new ObjectMapper());
        BonusProperties properties = new BonusProperties(1000, 100, TestEntities.ZONE,
                new BonusProperties.EventScheduler(false, Duration.ofMinutes(1)));
        service = new EventProcessingService(eventRepository, operationRepository, ruleRepository, accountRepository,
                campaignRepository, calculation, new OperationNumberGenerator(jdbcTemplate), events, new ObjectMapper(),
                transactionManager, properties, TestEntities.clock());

        lenient().when(employeeRepository.findById(3L)).thenReturn(Optional.of(d.maxim));
        lenient().when(ruleRepository.findById(1L)).thenReturn(Optional.of(sprint));
        lenient().when(ruleRepository.findApplicableByKpiCode(eq("SPRINT"), any())).thenReturn(List.of(sprint));
        lenient().when(campaignRepository.findRunningForDepartment(eq(1L), eq(CampaignStatus.ACTIVE), any()))
                .thenReturn(List.of(autumn));
        lenient().when(campaignRepository.getReferenceById(1L)).thenReturn(autumn);
        lenient().when(operationRepository.sumAmountByCampaign(eq(1L), any())).thenReturn(0L);
        lenient().when(accountRepository.lockByEmployeeId(3L)).thenReturn(Optional.of(d.maximAccount));
        lenient().when(jdbcTemplate.queryForObject(OperationNumberGenerator.NEXT_VALUE_SQL, Long.class)).thenReturn(330L);
    }

    private BusinessEvent given(long id, Integration source, String type, String payload, OffsetDateTime receivedAt) {
        BusinessEvent event = TestEntities.event(id, source, type, d.maxim, payload, receivedAt);
        lenient().when(eventRepository.lockById(id)).thenReturn(Optional.of(event));
        return event;
    }

    private BusinessEvent sprintEvent(String value) {
        return given(500, hris, "kpi_closed", "{\"kpi\": \"SPRINT\", \"value\": " + value + "}", RECEIVED);
    }

    private Operation savedOperation() {
        ArgumentCaptor<Operation> captor = ArgumentCaptor.forClass(Operation.class);
        verify(operationRepository).save(captor.capture());
        return captor.getValue();
    }

    // ── FR4-1: расчёт при поступлении KPI-события ──────────────────────────────────────

    @Test
    @DisplayName("FR4-1: при поступлении KPI-события выполняется расчёт бонуса по правилу показателя")
    void kpiEventTriggersCalculation() {
        sprintEvent("95");

        EventProcessingResult result = service.process(500L);

        assertThat(result.outcome()).isEqualTo(EventOutcome.ACCRUED);
        assertThat(result.message()).startsWith("3000 × 0.40 × 95 % = 1140");
        verify(ruleRepository).findApplicableByKpiCode("SPRINT", LocalDate.of(2026, 11, 24));
    }

    @Test
    @DisplayName("FR4-1: бизнес-дата расчёта — дата получения события по времени Минска")
    void businessDateUsesCompanyZone() {
        given(501, hris, "kpi_closed", "{\"kpi\": \"SPRINT\", \"value\": 95}",
                OffsetDateTime.parse("2026-11-30T22:30:00Z")); // в Минске уже 01.12 01:30

        service.process(501L);

        verify(ruleRepository).findApplicableByKpiCode("SPRINT", LocalDate.of(2026, 12, 1));
    }

    @Test
    @DisplayName("FR4-1: KPI ниже порога — событие обработано без начисления (BR-5)")
    void belowThresholdProcessedWithoutBonus() {
        BusinessEvent event = sprintEvent("70");

        EventProcessingResult result = service.process(500L);

        assertThat(result.outcome()).isEqualTo(EventOutcome.NO_BONUS);
        assertThat(event.getStatus()).isEqualTo(EventStatus.PROCESSED);
        verify(operationRepository, never()).save(any());
        assertThat(d.maximAccount.getBalance()).isEqualTo(9950);
    }

    @Test
    @DisplayName("FR4-1: событие без значения KPI откладывается с причиной ошибки и записью в аудит")
    void invalidEventIsPostponed() {
        BusinessEvent event = sprintEvent("null");

        EventProcessingResult result = service.process(500L);

        assertThat(result.outcome()).isEqualTo(EventOutcome.FAILED);
        assertThat(event.getStatus()).isEqualTo(EventStatus.FAILED);
        assertThat(event.getErrorMessage()).contains("значение KPI отсутствует");
        ArgumentCaptor<EventProcessingFailedEvent> failed = ArgumentCaptor.forClass(EventProcessingFailedEvent.class);
        verify(events).publishEvent(failed.capture());
        assertThat(failed.getValue().eventId()).isEqualTo(500L);
        assertThat(failed.getValue().employeeId()).isEqualTo(3L);
        verify(operationRepository, never()).save(any());
    }

    @Test
    @DisplayName("FR4-1: неизвестное событие — ошибка «не найдено»")
    void unknownEventIsNotFound() {
        when(eventRepository.lockById(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.process(404L)).isInstanceOf(ObjectNotFoundException.class);
    }

    // ── FR4-2: при активном правиле применяются коэффициенты ─────────────────────────────

    @Test
    @DisplayName("FR4-2: при активном правиле применяются коэффициенты KPI и кампании (1140 × 1,5 = 1710 → 1700)")
    void activeRuleAppliesCoefficients() {
        sprintEvent("95");

        service.process(500L);

        Operation op = savedOperation();
        assertThat(op.getAmount()).isEqualTo(1700);
        assertThat(op.getRule()).isSameAs(sprint);
        assertThat(op.getCampaign()).isSameAs(autumn);
        assertThat(op.getReason()).isEqualTo("Выполнение плана спринта, KPI 95 %");
    }

    @Test
    @DisplayName("FR4-2: без действующего (активного) правила для показателя начисление не создаётся")
    void noActiveRuleNoAccrual() {
        BusinessEvent event = given(502, hris, "kpi_closed", "{\"kpi\": \"RELEASE\", \"value\": 99}", RECEIVED);
        when(ruleRepository.findApplicableByKpiCode(eq("RELEASE"), any())).thenReturn(List.of());

        EventProcessingResult result = service.process(502L);

        assertThat(result.outcome()).isEqualTo(EventOutcome.FAILED);
        assertThat(event.getErrorMessage()).isEqualTo("Нет действующего правила для показателя RELEASE");
        verify(operationRepository, never()).save(any());
    }

    // ── FR4-3: сохранение начисления после расчёта ──────────────────────────────────────

    @Test
    @DisplayName("FR4-3: после расчёта сохраняется начисление со ссылкой на событие, баланс растёт, событие обработано")
    void savesAccrualAfterCalculation() {
        BusinessEvent event = sprintEvent("95");

        EventProcessingResult result = service.process(500L);

        Operation op = savedOperation();
        assertThat(op.getNumber()).isEqualTo("ОП-000330");
        assertThat(op.getType()).isEqualTo(OperationType.ACCRUAL);
        assertThat(op.getStatus()).isEqualTo(OperationStatus.ACCRUED);
        assertThat(op.getBasis()).isEqualTo(OperationBasis.KPI);
        assertThat(op.getEvent()).isSameAs(event);
        assertThat(op.getCreatedBy()).isNull(); // автоматическое начисление — «Система»
        assertThat(result.operationId()).isEqualTo(330L);
        assertThat(d.maximAccount.getBalance()).isEqualTo(9950 + 1700);
        assertThat(event.getStatus()).isEqualTo(EventStatus.PROCESSED);
        assertThat(event.getProcessedAt()).isEqualTo(TestEntities.now());

        ArgumentCaptor<OperationRegisteredEvent> published = ArgumentCaptor.forClass(OperationRegisteredEvent.class);
        verify(events).publishEvent(published.capture());
        assertThat(published.getValue().action()).isEqualTo(OperationAction.AUTO_ACCRUAL);
        assertThat(published.getValue().actorUserId()).isNull();
    }

    @Test
    @DisplayName("FR4-3: повторная обработка события не создаёт второе начисление (NFR-4)")
    void processingIsIdempotent() {
        BusinessEvent processed = sprintEvent("95");
        processed.markProcessed(TestEntities.now());
        given(503, hris, "kpi_closed", "{\"kpi\": \"SPRINT\", \"value\": 95}", RECEIVED);
        when(operationRepository.existsByEventId(503L)).thenReturn(true);

        assertThat(service.process(500L).outcome()).isEqualTo(EventOutcome.SKIPPED);
        assertThat(service.process(503L).outcome()).isEqualTo(EventOutcome.SKIPPED);
        verify(operationRepository, never()).save(any());
        assertThat(d.maximAccount.getBalance()).isEqualTo(9950);
    }

    @Test
    @DisplayName("FR4-3: на заблокированный счёт начисление не сохраняется — событие отложено")
    void blockedAccountPostponesEvent() {
        BusinessEvent event = sprintEvent("95");
        when(accountRepository.lockByEmployeeId(3L)).thenReturn(Optional.of(
                TestEntities.account(3, d.maxim, 9950, by.technosoft.bonus.domain.AccountStatus.BLOCKED)));

        assertThat(service.process(500L).outcome()).isEqualTo(EventOutcome.FAILED);
        assertThat(event.getErrorMessage()).isEqualTo("Счёт сотрудника заблокирован: начисление отложено");
        verify(operationRepository, never()).save(any());
    }

    // ── FR13-2: обработка внешних событий интеграций ─────────────────────────────────

    @Test
    @DisplayName("FR13-2: событие ERP «этап принят» обрабатывается по правилу STAGE (6000 × 0,5 = 3000)")
    void erpStageEventIsProcessed() {
        AccrualRule stage = TestEntities.rule(3, "Сдача этапа проекта", TestEntities.kpi(3, "STAGE", "Этап"), "0.50",
                6000, RulePeriodicity.EVENT, "100", "{\"roundTo\": 50}", true, LocalDate.of(2026, 6, 1));
        given(504, erp, "stage_accepted", "{\"project\": \"PRJ-7\"}", RECEIVED);
        when(ruleRepository.findApplicableByKpiCode(eq("STAGE"), any())).thenReturn(List.of(stage));
        when(ruleRepository.findById(3L)).thenReturn(Optional.of(stage));
        when(campaignRepository.findRunningForDepartment(eq(1L), eq(CampaignStatus.ACTIVE), any())).thenReturn(List.of());

        EventProcessingResult result = service.process(504L);

        assertThat(result.outcome()).isEqualTo(EventOutcome.ACCRUED);
        assertThat(savedOperation().getAmount()).isEqualTo(3000);
        assertThat(savedOperation().getReason()).isEqualTo("Сдача этапа проекта");
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({"anniversary, TENURE", "stage_accepted, STAGE", "certificate_obtained, CERT"})
    @DisplayName("FR13-2: тип внешнего события сопоставляется с показателем правила")
    void eventTypeMapsToKpi(String type, String kpi) {
        BusinessEvent event = TestEntities.event(505, erp, type, d.maxim, "{}", RECEIVED);
        assertThat(service.parse(event).kpiCode()).isEqualTo(kpi);
        assertThat(service.parse(event).value()).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "payroll_closed|{}|Неизвестный тип события: payroll_closed",
            "kpi_closed|{не json|payload события не является JSON",
            "kpi_closed|{\"value\": 90}|не указан показатель KPI",
            "kpi_closed|{\"kpi\": \"SPRINT\", \"value\": \"высокий\"}|значение KPI не является числом"})
    @DisplayName("FR13-2: некорректное внешнее событие не приводит к начислению и помечается ошибкой")
    void malformedExternalEventsFail(String type, String payload, String message) {
        BusinessEvent event = given(506, hris, type, payload, RECEIVED);

        EventProcessingResult result = service.process(506L);

        assertThat(result.outcome()).isEqualTo(EventOutcome.FAILED);
        assertThat(event.getErrorMessage()).contains(message);
        verify(operationRepository, never()).save(any());
    }

    // ── FR13-3: автоматическая обработка очереди (синхронизация) ────────────────────────

    @Test
    @DisplayName("FR13-3: пакетная обработка берёт все новые события; сбой одного не останавливает остальные")
    void batchProcessesAllNewEvents() {
        EventProcessingService spyService = spy(service);
        when(eventRepository.findIdsByStatus(eq(EventStatus.NEW), any())).thenReturn(List.of(1L, 2L, 3L, 4L, 5L));
        doReturn(new EventProcessingResult(1L, EventOutcome.ACCRUED, 10L, "ok")).when(spyService).process(1L);
        doReturn(new EventProcessingResult(2L, EventOutcome.NO_BONUS, null, "0")).when(spyService).process(2L);
        doReturn(new EventProcessingResult(3L, EventOutcome.SKIPPED, null, "дубль")).when(spyService).process(3L);
        doReturn(new EventProcessingResult(4L, EventOutcome.FAILED, null, "ошибка")).when(spyService).process(4L);
        doThrow(new IllegalStateException("сбой БД")).when(spyService).process(5L);
        BusinessEvent crashed = TestEntities.event(5, hris, "kpi_closed", d.maxim, "{}", RECEIVED);
        when(eventRepository.findById(5L)).thenReturn(Optional.of(crashed));

        BatchProcessingResult result = spyService.processPending();

        assertThat(result).isEqualTo(new BatchProcessingResult(5, 1, 1, 1, 2));
        assertThat(crashed.getStatus()).isEqualTo(EventStatus.FAILED);
        assertThat(crashed.getErrorMessage()).isEqualTo("Внутренняя ошибка обработки: IllegalStateException");
    }

    @Test
    @DisplayName("FR13-3: пустая очередь событий обрабатывается без ошибок")
    void emptyBatch() {
        when(eventRepository.findIdsByStatus(eq(EventStatus.NEW), any())).thenReturn(List.of());
        assertThat(service.processPending()).isEqualTo(new BatchProcessingResult(0, 0, 0, 0, 0));
    }
}
