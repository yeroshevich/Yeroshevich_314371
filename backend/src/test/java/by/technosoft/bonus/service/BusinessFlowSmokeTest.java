package by.technosoft.bonus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import by.technosoft.bonus.domain.BusinessEvent;
import by.technosoft.bonus.domain.EventStatus;
import by.technosoft.bonus.domain.Operation;
import by.technosoft.bonus.domain.OperationBasis;
import by.technosoft.bonus.domain.OperationStatus;
import by.technosoft.bonus.domain.OperationType;
import by.technosoft.bonus.domain.ReviewDecision;
import by.technosoft.bonus.repository.AuditLogRepository;
import by.technosoft.bonus.repository.BonusAccountRepository;
import by.technosoft.bonus.repository.BusinessEventRepository;
import by.technosoft.bonus.repository.EmployeeRepository;
import by.technosoft.bonus.repository.IntegrationRepository;
import by.technosoft.bonus.repository.OperationRepository;
import by.technosoft.bonus.repository.OperationReviewRepository;
import by.technosoft.bonus.service.dto.AccrualPreview;
import by.technosoft.bonus.service.dto.AccrualRequest;
import by.technosoft.bonus.service.dto.AccrualResult;
import by.technosoft.bonus.service.dto.BatchProcessingResult;
import by.technosoft.bonus.service.dto.CalculationRequest;
import by.technosoft.bonus.service.dto.CalculationResult;
import by.technosoft.bonus.service.dto.CorrectionRequest;
import by.technosoft.bonus.service.dto.CorrectionResult;
import by.technosoft.bonus.service.dto.EventOutcome;
import by.technosoft.bonus.service.dto.EventProcessingResult;
import by.technosoft.bonus.service.dto.OperationView;
import by.technosoft.bonus.service.dto.RedemptionResult;
import by.technosoft.bonus.service.exception.AccessDeniedDomainException;
import by.technosoft.bonus.service.exception.AccountBlockedException;
import by.technosoft.bonus.service.exception.InsufficientBalanceException;
import by.technosoft.bonus.service.exception.InvalidCalculationInputException;
import by.technosoft.bonus.service.exception.OperationStateException;
import by.technosoft.bonus.service.exception.ProgramUnavailableException;
import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;

/**
 * Smoke-прогон бизнес-логики на демо-данных V2. Каждый тест выполняется в транзакции,
 * которая откатывается после теста, поэтому данные БД не меняются.
 * Пользователи из V2: 2 — руководитель разработки (команда: сотрудники 1, 3–7, 26),
 * 20 — HR, 23 — финотдел, 1 — сотрудник. Счёт сотрудника 9 заблокирован.
 */
@SpringBootTest
@Transactional
@Import(BusinessFlowSmokeTest.FixedClockConfig.class)
class BusinessFlowSmokeTest {

    private static final long MANAGER = 2L;
    private static final long HR = 20L;
    private static final long FINANCE = 23L;
    private static final long EMPLOYEE_USER = 1L;

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            ZoneId zone = ZoneId.of("Europe/Minsk");
            return Clock.fixed(ZonedDateTime.of(2026, 11, 25, 12, 0, 0, 0, zone).toInstant(), zone);
        }
    }

    @Autowired private AccrualService accrualService;
    @Autowired private RedemptionService redemptionService;
    @Autowired private CorrectionService correctionService;
    @Autowired private ReviewService reviewService;
    @Autowired private EventProcessingService eventProcessingService;
    @Autowired private BonusCalculationService calculationService;
    @Autowired private BonusAccountRepository accountRepository;
    @Autowired private OperationRepository operationRepository;
    @Autowired private OperationReviewRepository reviewRepository;
    @Autowired private BusinessEventRepository eventRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private IntegrationRepository integrationRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void calculationMatchesSeedOperations() {
        // ОП-000001: KPI 116 % по правилу 1 в июне (кампании ещё нет) → 3000 × 0,4 × 1,16 = 1392 → 1400
        CalculationResult june = calculationService.calculate(
                new CalculationRequest(12L, 1L, new BigDecimal("116"), LocalDate.of(2026, 6, 25)));
        assertThat(june.total()).isEqualTo(1400);
        assertThat(june.campaignId()).isNull();

        // ОП-000144: KPI 91 %, отдел разработки в кампании «Осенний релиз 2.0» (×1,5) → 1092 × 1,5 = 1638 → 1650
        CalculationResult november = calculationService.calculate(
                new CalculationRequest(7L, 1L, new BigDecimal("91"), LocalDate.of(2026, 11, 20)));
        assertThat(november.total()).isEqualTo(1650);
        assertThat(november.campaignId()).isEqualTo(1L);

        // BR-5: ниже порога 80 % — 0
        assertThat(calculationService.calculate(
                new CalculationRequest(7L, 1L, new BigDecimal("79.99"), LocalDate.of(2026, 11, 20))).isZero()).isTrue();

        // Альт. поток UC6: нет значения KPI / неактивное правило
        assertThatThrownBy(() -> calculationService.calculate(
                new CalculationRequest(7L, 1L, null, LocalDate.of(2026, 11, 20))))
                .isInstanceOf(InvalidCalculationInputException.class)
                .hasMessageContaining("значение KPI отсутствует");
        assertThatThrownBy(() -> calculationService.calculate(
                new CalculationRequest(7L, 6L, BigDecimal.TEN, LocalDate.of(2026, 11, 20))))
                .isInstanceOf(InvalidCalculationInputException.class);
    }

    @Test
    void manualAccrualPreviewAndCreate() {
        AccrualPreview preview = accrualService.preview(MANAGER,
                request(List.of(3L, 1L), 600, 1L));
        assertThat(preview.lines()).extracting(l -> l.employeeId()).containsExactly(1L, 3L);
        assertThat(preview.lines()).allSatisfy(l -> {
            assertThat(l.coefficient()).isEqualByComparingTo("1.5");
            assertThat(l.total()).isEqualTo(900);
            assertThat(l.needsApproval()).isFalse();
        });
        assertThat(preview.total()).isEqualTo(1800);
        assertThat(accrualService.preview(MANAGER, request(List.of(1L), 800, 1L)).approvalCount()).isEqualTo(1);

        // ≤ лимита → accrued, баланс растёт
        int before1 = balance(1L);
        AccrualResult accrued = accrualService.create(MANAGER, request(List.of(1L), 500, null));
        OperationView op = accrued.operations().get(0);
        assertThat(op.status()).isEqualTo(OperationStatus.ACCRUED);
        assertThat(op.number()).isEqualTo(OperationNumberGenerator.format(op.id()));
        assertThat(balance(1L)).isEqualTo(before1 + 500);
        assertThat(auditLogRepository.findByObjectTypeAndObjectIdOrderByOccurredAtDesc("operation", op.id()))
                .extracting(a -> a.getAction()).containsExactly("Ручное начисление");

        // > лимита → pending, баланс не меняется
        int before3 = balance(3L);
        AccrualResult pending = accrualService.create(MANAGER, request(List.of(3L), 1200, null));
        assertThat(pending.pendingCount()).isEqualTo(1);
        assertThat(pending.operations().get(0).status()).isEqualTo(OperationStatus.PENDING);
        assertThat(balance(3L)).isEqualTo(before3);

        // права и счёт
        assertThatThrownBy(() -> accrualService.create(MANAGER, request(List.of(9L), 100, null)))
                .isInstanceOf(AccessDeniedDomainException.class);
        assertThatThrownBy(() -> accrualService.create(EMPLOYEE_USER, request(List.of(3L), 100, null)))
                .isInstanceOf(AccessDeniedDomainException.class);
        assertThatThrownBy(() -> accrualService.create(HR, request(List.of(9L), 100, null)))
                .isInstanceOf(AccountBlockedException.class);
        assertThatThrownBy(() -> accrualService.create(HR, request(List.of(3L), 0, null)))
                .isInstanceOf(ConstraintViolationException.class);

        assertBalancesMatchJournal();
    }

    @Test
    void redemption() {
        int before = balance(1L);
        RedemptionResult result = redemptionService.redeem(1L, 2L);
        assertThat(result.operation().type()).isEqualTo(OperationType.WRITEOFF);
        assertThat(result.operation().status()).isEqualTo(OperationStatus.WRITTEN_OFF);
        assertThat(result.operation().amount()).isEqualTo(-900);
        assertThat(result.balance()).isEqualTo(before - 900);

        assertThatThrownBy(() -> redemptionService.redeem(26L, 1L))
                .isInstanceOfSatisfying(InsufficientBalanceException.class,
                        e -> assertThat(e.getShortage()).isEqualTo(1500));
        assertThatThrownBy(() -> redemptionService.redeem(1L, 8L)).isInstanceOf(ProgramUnavailableException.class);
        assertThatThrownBy(() -> redemptionService.redeem(9L, 1L)).isInstanceOf(AccountBlockedException.class);

        // Маппинг JSONB и INET читает демо-записи аудита
        entityManager.flush();
        assertThat(auditLogRepository.findAll()).anySatisfy(a -> {
            assertThat(a.getIp()).isNotNull();
            assertThat(a.getDetails()).startsWith("{");
        });

        assertBalancesMatchJournal();
    }

    @Test
    void correctionAndReview() {
        // Обычная корректировка: ОП-000014 (сотрудник 6, 1000 Б) → 700 Б
        int before6 = balance(6L);
        CorrectionResult adjusted = correctionService.correct(14L, CorrectionRequest.adjust(700, "Ошибка в сумме"), MANAGER);
        assertThat(adjusted.pendingApproval()).isFalse();
        assertThat(adjusted.original().status()).isEqualTo(OperationStatus.CANCELLED);
        assertThat(adjusted.correction().status()).isEqualTo(OperationStatus.CORRECTED);
        assertThat(adjusted.correction().originalAmount()).isEqualTo(1000);
        assertThat(adjusted.correction().correctsId()).isEqualTo(14L);
        assertThat(balance(6L)).isEqualTo(before6 - 300);

        // Корректировка с уходом в pending: у сотрудника 26 после списания остаётся меньше исходной суммы
        OperationView accrual = accrualService.create(HR, request(List.of(26L), 1000, null)).operations().get(0);
        redemptionService.redeem(26L, 2L);
        assertThat(balance(26L)).isEqualTo(100);
        CorrectionResult cancel = correctionService.correct(accrual.id(), CorrectionRequest.cancel("Начислено ошибочно"), HR);
        assertThat(cancel.pendingApproval()).isTrue();
        assertThat(cancel.correction().status()).isEqualTo(OperationStatus.PENDING);
        assertThat(cancel.correction().amount()).isEqualTo(-1000);
        assertThat(cancel.original().status()).isEqualTo(OperationStatus.ACCRUED);
        assertThat(balance(26L)).isEqualTo(100);
        assertThatThrownBy(() -> correctionService.correct(accrual.id(), CorrectionRequest.adjust(500, "Ещё раз"), HR))
                .isInstanceOf(OperationStateException.class);

        // Подтверждение корректировки, пока баланс уйдёт в минус, невозможно; после пополнения — проходит
        Long pendingCorrectionId = cancel.correction().id();
        assertThatThrownBy(() -> reviewService.approve(pendingCorrectionId, FINANCE, null))
                .isInstanceOf(InsufficientBalanceException.class);
        accrualService.create(HR, request(List.of(26L), 1000, null));
        OperationView approvedCorrection = reviewService.approve(pendingCorrectionId, FINANCE, "Подтверждено");
        assertThat(approvedCorrection.status()).isEqualTo(OperationStatus.CANCELLED);
        assertThat(operationRepository.findById(accrual.id()).orElseThrow().getStatus())
                .isEqualTo(OperationStatus.CANCELLED);
        assertThat(balance(26L)).isEqualTo(100);

        // approve начисления сверх лимита: баланс растёт, решение записано
        OperationView pending = accrualService.create(MANAGER, request(List.of(3L), 1200, null)).operations().get(0);
        int before3 = balance(3L);
        OperationView approved = reviewService.approve(pending.id(), FINANCE, null);
        assertThat(approved.status()).isEqualTo(OperationStatus.ACCRUED);
        assertThat(balance(3L)).isEqualTo(before3 + 1200);
        assertThatThrownBy(() -> reviewService.approve(pending.id(), FINANCE, null))
                .isInstanceOf(OperationStateException.class);

        // block: ОП-000146 из демо-данных (pending, сотрудник 1)
        int before1 = balance(1L);
        assertThatThrownBy(() -> reviewService.block(146L, MANAGER, "Нет прав"))
                .isInstanceOf(AccessDeniedDomainException.class);
        OperationView blocked = reviewService.block(146L, FINANCE, "Дублирует начисление");
        assertThat(blocked.status()).isEqualTo(OperationStatus.BLOCKED);
        assertThat(balance(1L)).isEqualTo(before1);
        entityManager.flush();
        assertThat(reviewRepository.findAll()).anySatisfy(r -> {
            assertThat(r.getOperation().getId()).isEqualTo(146L);
            assertThat(r.getDecision()).isEqualTo(ReviewDecision.BLOCKED);
        });

        assertBalancesMatchJournal();
    }

    @Test
    void eventProcessingIsIdempotentAndBatchSurvivesFailures() {
        BusinessEvent sprint = newEvent("SMOKE-SPRINT-03", "kpi_closed", 3L, "{\"kpi\": \"SPRINT\", \"value\": 95}");
        int before3 = balance(3L);

        EventProcessingResult first = eventProcessingService.process(sprint.getId());
        assertThat(first.outcome()).isEqualTo(EventOutcome.ACCRUED);
        Operation op = operationRepository.findById(first.operationId()).orElseThrow();
        // 3000 × 0,4 × 95 % = 1140; × 1,5 (кампания) = 1710 → 1700
        assertThat(op.getAmount()).isEqualTo(1700);
        assertThat(op.getBasis()).isEqualTo(OperationBasis.KPI);
        assertThat(op.getCampaign().getId()).isEqualTo(1L);
        assertThat(op.getCreatedBy()).isNull();
        assertThat(sprint.getStatus()).isEqualTo(EventStatus.PROCESSED);
        assertThat(balance(3L)).isEqualTo(before3 + 1700);

        // Повторная обработка — без дубля (NFR-4)
        EventProcessingResult second = eventProcessingService.process(sprint.getId());
        assertThat(second.outcome()).isEqualTo(EventOutcome.SKIPPED);
        assertThat(operationRepository.findByEventId(sprint.getId())).hasSize(1);
        assertThat(balance(3L)).isEqualTo(before3 + 1700);

        // Пакет: ниже порога, без значения KPI (ошибка) и годовщина — ошибка не останавливает пакет
        BusinessEvent low = newEvent("SMOKE-SPRINT-04", "kpi_closed", 4L, "{\"kpi\": \"SPRINT\", \"value\": 70}");
        BusinessEvent broken = newEvent("SMOKE-SPRINT-05", "kpi_closed", 5L, "{\"kpi\": \"SPRINT\", \"value\": null}");
        BusinessEvent anniversary = newEvent("SMOKE-TENURE-17", "anniversary", 17L, "{\"years\": 9}");
        int before17 = balance(17L);

        BatchProcessingResult batch = eventProcessingService.processPending();
        assertThat(batch.total()).isEqualTo(3);
        assertThat(batch.accrued()).isEqualTo(1);
        assertThat(batch.noBonus()).isEqualTo(1);
        assertThat(batch.failed()).isEqualTo(1);

        assertThat(low.getStatus()).isEqualTo(EventStatus.PROCESSED);
        assertThat(operationRepository.existsByEventId(low.getId())).isFalse();
        assertThat(broken.getStatus()).isEqualTo(EventStatus.FAILED);
        assertThat(broken.getErrorMessage()).contains("значение KPI отсутствует");
        assertThat(operationRepository.existsByEventId(broken.getId())).isFalse();
        assertThat(auditLogRepository.findByObjectTypeAndObjectIdOrderByOccurredAtDesc("business_event", broken.getId()))
                .hasSize(1);
        assertThat(balance(17L)).isEqualTo(before17 + 1000); // 1000 × 1,0, кампания отдела не активна

        assertThat(eventProcessingService.processPending().total()).isZero();
        assertBalancesMatchJournal();
    }

    private AccrualRequest request(List<Long> employeeIds, int amount, Long campaignId) {
        return new AccrualRequest(employeeIds, amount, OperationBasis.ACHIEVEMENT, "Smoke-тест: доклад на митапе",
                null, campaignId);
    }

    private BusinessEvent newEvent(String externalId, String type, Long employeeId, String payload) {
        return eventRepository.save(BusinessEvent.received(
                type.equals("kpi_closed") ? integrationRepository.getReferenceById(1L) : null,
                externalId, type, employeeRepository.getReferenceById(employeeId), payload,
                OffsetDateTime.parse("2026-11-24T12:00:00+03:00")));
    }

    private int balance(Long employeeId) {
        return accountRepository.findByEmployeeId(employeeId).orElseThrow().getBalance();
    }

    /** BR-6: баланс каждого счёта равен сумме проведённых операций (сверка из ЛР7). */
    private void assertBalancesMatchJournal() {
        entityManager.flush();
        Integer mismatches = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM bonus_account a
                WHERE a.balance <> (SELECT COALESCE(SUM(o.amount), 0) FROM operation o
                                    WHERE o.account_id = a.id AND o.status IN ('accrued', 'written_off', 'corrected'))
                """, Integer.class);
        assertThat(mismatches).isZero();
    }
}
