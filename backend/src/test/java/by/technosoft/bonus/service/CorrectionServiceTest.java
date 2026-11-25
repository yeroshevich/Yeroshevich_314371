package by.technosoft.bonus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;

import by.technosoft.bonus.domain.AppUser;
import by.technosoft.bonus.domain.BonusAccount;
import by.technosoft.bonus.domain.DemoData;
import by.technosoft.bonus.domain.Operation;
import by.technosoft.bonus.domain.OperationBasis;
import by.technosoft.bonus.domain.OperationStatus;
import by.technosoft.bonus.domain.OperationType;
import by.technosoft.bonus.domain.TestEntities;
import by.technosoft.bonus.repository.AppUserRepository;
import by.technosoft.bonus.repository.BonusAccountRepository;
import by.technosoft.bonus.repository.OperationRepository;
import by.technosoft.bonus.service.dto.CorrectionRequest;
import by.technosoft.bonus.service.dto.CorrectionResult;
import by.technosoft.bonus.service.event.OperationAction;
import by.technosoft.bonus.service.event.OperationRegisteredEvent;
import by.technosoft.bonus.service.exception.AccessDeniedDomainException;
import by.technosoft.bonus.service.exception.AccountBlockedException;
import by.technosoft.bonus.service.exception.DomainValidationException;
import by.technosoft.bonus.service.exception.ObjectNotFoundException;
import by.technosoft.bonus.service.exception.OperationStateException;

/** UC7 «Корректировка начислений» (алгоритм А5): допустимость, пересчёт баланса, фиксация изменения. */
@ExtendWith(MockitoExtension.class)
@DisplayName("CorrectionService — корректировка начислений (UC7)")
class CorrectionServiceTest {

    private static final long HR = 20L;
    private static final long MANAGER = 2L;

    @Mock private AppUserRepository userRepository;
    @Mock private OperationRepository operationRepository;
    @Mock private BonusAccountRepository accountRepository;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private ApplicationEventPublisher events;

    private final DemoData d = new DemoData();
    private Operation accrual; // ОП-000014: начисление 1 000 Б Анне Ковалёвой
    private CorrectionService service;

    @BeforeEach
    void setUp() {
        service = new CorrectionService(new AccessPolicy(userRepository), operationRepository, accountRepository,
                new OperationNumberGenerator(jdbcTemplate), events, TestEntities.clock());
        accrual = TestEntities.operation(14, d.annaAccount, OperationType.ACCRUAL, OperationStatus.ACCRUED,
                OperationBasis.PROJECT, 1000, TestEntities.at("2026-11-10", 10));
        for (AppUser u : new AppUser[] {d.employeeUser, d.managerUser, d.hrUser, d.financeUser}) {
            lenient().when(userRepository.findWithRoleById(u.getId())).thenReturn(Optional.of(u));
        }
        lenient().when(operationRepository.lockById(14L)).thenReturn(Optional.of(accrual));
        lenient().when(accountRepository.lockById(1L)).thenReturn(Optional.of(d.annaAccount));
        lenient().when(jdbcTemplate.queryForObject(OperationNumberGenerator.NEXT_VALUE_SQL, Long.class)).thenReturn(320L);
    }

    private Operation savedCorrection() {
        ArgumentCaptor<Operation> captor = ArgumentCaptor.forClass(Operation.class);
        verify(operationRepository).save(captor.capture());
        return captor.getValue();
    }

    // ── FR7-1: корректировка разрешена при обнаружении ошибки ───────────────────────────

    @Test
    @DisplayName("FR7-1: HR исправляет ошибочную сумму — создаётся операция корректировки со ссылкой на исходную")
    void hrCorrectsAccrual() {
        CorrectionResult result = service.correct(14L, CorrectionRequest.adjust(700, " Ошибка в сумме "), HR);

        Operation correction = savedCorrection();
        assertThat(correction.getType()).isEqualTo(OperationType.CORRECTION);
        assertThat(correction.getBasis()).isEqualTo(OperationBasis.CORRECTION);
        assertThat(correction.getStatus()).isEqualTo(OperationStatus.CORRECTED);
        assertThat(correction.getAmount()).isEqualTo(700);
        assertThat(correction.getOriginalAmount()).isEqualTo(1000);
        assertThat(correction.getCorrects()).isSameAs(accrual);
        assertThat(correction.getReason()).isEqualTo("Ошибка в сумме");
        assertThat(result.pendingApproval()).isFalse();
        assertThat(result.original().status()).isEqualTo(OperationStatus.CANCELLED);
    }

    @Test
    @DisplayName("FR7-1: руководитель корректирует начисления только своей команды")
    void managerCorrectsOnlyOwnTeam() {
        assertThat(service.correct(14L, CorrectionRequest.adjust(800, "Пересчёт"), MANAGER).correction().amount())
                .isEqualTo(800);

        Operation foreign = TestEntities.operation(40, d.pavelAccount, OperationType.ACCRUAL, OperationStatus.ACCRUED,
                OperationBasis.KPI, 500, TestEntities.at("2026-11-01", 10));
        when(operationRepository.lockById(40L)).thenReturn(Optional.of(foreign));
        assertThatThrownBy(() -> service.correct(40L, CorrectionRequest.adjust(300, "Чужой"), MANAGER))
                .isInstanceOf(AccessDeniedDomainException.class);
    }

    @Test
    @DisplayName("FR7-1: сотрудник и финотдел корректировать начисления не могут")
    void otherRolesCannotCorrect() {
        assertThatThrownBy(() -> service.correct(14L, CorrectionRequest.adjust(700, "x"), 1L))
                .isInstanceOf(AccessDeniedDomainException.class);
        assertThatThrownBy(() -> service.correct(14L, CorrectionRequest.adjust(700, "x"), 23L))
                .isInstanceOf(AccessDeniedDomainException.class);
        verify(operationRepository, never()).save(any());
    }

    @Test
    @DisplayName("FR7-1: списание и уже отменённую операцию корректировать нельзя")
    void writeoffAndCancelledCannotBeCorrected() {
        Operation writeoff = TestEntities.operation(41, d.annaAccount, OperationType.WRITEOFF,
                OperationStatus.WRITTEN_OFF, OperationBasis.PROGRAM, -900, TestEntities.at("2026-11-02", 10));
        Operation cancelled = TestEntities.operation(42, d.annaAccount, OperationType.ACCRUAL,
                OperationStatus.CANCELLED, OperationBasis.KPI, 500, TestEntities.at("2026-11-03", 10));
        when(operationRepository.lockById(41L)).thenReturn(Optional.of(writeoff));
        when(operationRepository.lockById(42L)).thenReturn(Optional.of(cancelled));

        assertThatThrownBy(() -> service.correct(41L, CorrectionRequest.adjust(500, "x"), HR))
                .isInstanceOf(OperationStateException.class)
                .hasMessageContaining("written_off");
        assertThatThrownBy(() -> service.correct(42L, CorrectionRequest.cancel("x"), HR))
                .isInstanceOf(OperationStateException.class)
                .hasMessageContaining("cancelled");
        verify(operationRepository, never()).save(any());
    }

    @Test
    @DisplayName("FR7-1: повторная корректировка, пока предыдущая на согласовании, запрещена")
    void secondCorrectionWhilePendingIsRejected() {
        when(operationRepository.existsByCorrectsIdAndStatus(14L, OperationStatus.PENDING)).thenReturn(true);

        assertThatThrownBy(() -> service.correct(14L, CorrectionRequest.adjust(500, "Ещё раз"), HR))
                .isInstanceOf(OperationStateException.class)
                .hasMessageContaining("уже есть корректировка на согласовании");
    }

    @Test
    @DisplayName("FR7-1: новая сумма должна быть больше нуля и отличаться от текущей")
    void newAmountIsValidated() {
        assertThatThrownBy(() -> service.correct(14L, CorrectionRequest.adjust(1000, "Та же"), HR))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("совпадает с текущей");
        assertThatThrownBy(() -> service.correct(14L,
                new CorrectionRequest(by.technosoft.bonus.service.dto.CorrectionAction.ADJUST, null, "Без суммы"), HR))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("больше нуля");
        assertThat(d.annaAccount.getBalance()).isEqualTo(8900);
    }

    @Test
    @DisplayName("FR7-1: неизвестная операция или заблокированный счёт — корректировка невозможна")
    void unknownOperationOrBlockedAccount() {
        when(operationRepository.lockById(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.correct(404L, CorrectionRequest.cancel("x"), HR))
                .isInstanceOf(ObjectNotFoundException.class);

        Operation pavelOp = TestEntities.operation(43, d.pavelAccount, OperationType.ACCRUAL, OperationStatus.ACCRUED,
                OperationBasis.KPI, 500, TestEntities.at("2026-10-01", 10));
        when(operationRepository.lockById(43L)).thenReturn(Optional.of(pavelOp));
        when(accountRepository.lockById(9L)).thenReturn(Optional.of(d.pavelAccount));
        assertThatThrownBy(() -> service.correct(43L, CorrectionRequest.adjust(300, "x"), HR))
                .isInstanceOf(AccountBlockedException.class);
    }

    // ── FR7-2: пересчёт баланса ─────────────────────────────────────────────────────

    @Test
    @DisplayName("FR7-2: уменьшение суммы 1 000 → 700 уменьшает баланс на 300")
    void decreaseRecalculatesBalance() {
        service.correct(14L, CorrectionRequest.adjust(700, "Ошибка"), HR);

        assertThat(d.annaAccount.getBalance()).isEqualTo(8600);
        assertThat(accrual.getStatus()).isEqualTo(OperationStatus.CANCELLED);
    }

    @Test
    @DisplayName("FR7-2: увеличение суммы 1 000 → 1 500 увеличивает баланс на 500")
    void increaseRecalculatesBalance() {
        service.correct(14L, CorrectionRequest.adjust(1500, "Недоначислено"), HR);
        assertThat(d.annaAccount.getBalance()).isEqualTo(9400);
    }

    @Test
    @DisplayName("FR7-2: отмена начисления вычитает его сумму из баланса, корректировка получает статус «Отменено»")
    void cancellationRecalculatesBalance() {
        CorrectionResult result = service.correct(14L, CorrectionRequest.cancel("Начислено ошибочно"), HR);

        Operation correction = savedCorrection();
        assertThat(correction.getAmount()).isEqualTo(-1000);
        assertThat(correction.getStatus()).isEqualTo(OperationStatus.CANCELLED);
        assertThat(correction.isCancellationRequest()).isTrue();
        assertThat(d.annaAccount.getBalance()).isEqualTo(7900);
        assertThat(result.original().status()).isEqualTo(OperationStatus.CANCELLED);
    }

    @Test
    @DisplayName("FR7-2: если баланс уйдёт в минус — корректировка на согласование, баланс и исходная операция не меняются")
    void negativeBalanceGoesToApproval() {
        BonusAccount poor = TestEntities.account(26, d.anna, 100);
        Operation op = TestEntities.operation(50, poor, OperationType.ACCRUAL, OperationStatus.ACCRUED,
                OperationBasis.ACHIEVEMENT, 1000, TestEntities.at("2026-11-20", 10));
        when(operationRepository.lockById(50L)).thenReturn(Optional.of(op));
        when(accountRepository.lockById(26L)).thenReturn(Optional.of(poor));

        CorrectionResult result = service.correct(50L, CorrectionRequest.cancel("Ошибочно"), HR);

        assertThat(result.pendingApproval()).isTrue();
        assertThat(result.correction().status()).isEqualTo(OperationStatus.PENDING);
        assertThat(result.original().status()).isEqualTo(OperationStatus.ACCRUED);
        assertThat(poor.getBalance()).isEqualTo(100);
    }

    @Test
    @DisplayName("FR7-2: повторная корректировка уже скорректированной суммы пересчитывает баланс от неё")
    void correctionOfCorrection() {
        Operation corrected = TestEntities.operation(60, d.annaAccount, OperationType.CORRECTION,
                OperationStatus.CORRECTED, OperationBasis.CORRECTION, 700, TestEntities.at("2026-11-21", 10));
        when(operationRepository.lockById(60L)).thenReturn(Optional.of(corrected));

        service.correct(60L, CorrectionRequest.adjust(900, "Уточнение"), HR);

        assertThat(d.annaAccount.getBalance()).isEqualTo(9100);
        assertThat(corrected.getStatus()).isEqualTo(OperationStatus.CANCELLED);
        assertThat(savedCorrection().getOriginalAmount()).isEqualTo(700);
    }

    // ── FR7-3: фиксация изменения в журнале ────────────────────────────────────────

    @Test
    @DisplayName("FR7-3: корректировка публикуется для журнала с исходной и новой суммой и автором")
    void correctionIsJournaled() {
        service.correct(14L, CorrectionRequest.adjust(700, "Ошибка в сумме"), HR);

        ArgumentCaptor<OperationRegisteredEvent> event = ArgumentCaptor.forClass(OperationRegisteredEvent.class);
        verify(events).publishEvent(event.capture());
        OperationRegisteredEvent e = event.getValue();
        assertThat(e.action()).isEqualTo(OperationAction.CORRECTION);
        assertThat(e.operationNumber()).isEqualTo("ОП-000320");
        assertThat(e.originalAmount()).isEqualTo(1000);
        assertThat(e.amount()).isEqualTo(700);
        assertThat(e.actorUserId()).isEqualTo(HR);
        assertThat(e.status()).isEqualTo(OperationStatus.CORRECTED);
        assertThat(e.comment()).isNull();
    }

    @Test
    @DisplayName("FR7-3: отмена и корректировка на согласовании фиксируются с пояснением для финотдела")
    void cancellationAndPendingAreJournaled() {
        BonusAccount poor = TestEntities.account(26, d.anna, 100);
        Operation op = TestEntities.operation(50, poor, OperationType.ACCRUAL, OperationStatus.ACCRUED,
                OperationBasis.ACHIEVEMENT, 1000, TestEntities.at("2026-11-20", 10));
        when(operationRepository.lockById(50L)).thenReturn(Optional.of(op));
        when(accountRepository.lockById(26L)).thenReturn(Optional.of(poor));

        service.correct(50L, CorrectionRequest.cancel("Ошибочно"), HR);

        ArgumentCaptor<OperationRegisteredEvent> event = ArgumentCaptor.forClass(OperationRegisteredEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().action()).isEqualTo(OperationAction.CANCELLATION);
        assertThat(event.getValue().comment()).isEqualTo("Баланс станет −900\u00A0Б: нужно согласование финотдела");
    }
}
