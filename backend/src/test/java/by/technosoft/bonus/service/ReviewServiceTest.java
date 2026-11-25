package by.technosoft.bonus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import by.technosoft.bonus.domain.AppUser;
import by.technosoft.bonus.domain.BonusAccount;
import by.technosoft.bonus.domain.DemoData;
import by.technosoft.bonus.domain.Operation;
import by.technosoft.bonus.domain.OperationBasis;
import by.technosoft.bonus.domain.OperationReview;
import by.technosoft.bonus.domain.OperationStatus;
import by.technosoft.bonus.domain.OperationType;
import by.technosoft.bonus.domain.ReviewDecision;
import by.technosoft.bonus.domain.TestEntities;
import by.technosoft.bonus.repository.AppUserRepository;
import by.technosoft.bonus.repository.BonusAccountRepository;
import by.technosoft.bonus.repository.OperationRepository;
import by.technosoft.bonus.repository.OperationReviewRepository;
import by.technosoft.bonus.service.dto.OperationView;
import by.technosoft.bonus.service.event.OperationAction;
import by.technosoft.bonus.service.event.OperationRegisteredEvent;
import by.technosoft.bonus.service.exception.AccessDeniedDomainException;
import by.technosoft.bonus.service.exception.AccountBlockedException;
import by.technosoft.bonus.service.exception.DomainValidationException;
import by.technosoft.bonus.service.exception.InsufficientBalanceException;
import by.technosoft.bonus.service.exception.OperationStateException;

/**
 * Контроль финансового отдела: подтверждение начислений выше лимита (UC5) и корректировок,
 * уводящих баланс в минус (UC7). Подтверждение — момент обновления баланса.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewService — согласование финотделом (UC5, UC7)")
class ReviewServiceTest {

    private static final long FINANCE = 23L;

    @Mock private AppUserRepository userRepository;
    @Mock private OperationRepository operationRepository;
    @Mock private BonusAccountRepository accountRepository;
    @Mock private OperationReviewRepository reviewRepository;
    @Mock private ApplicationEventPublisher events;

    private final DemoData d = new DemoData();
    private Operation pendingAccrual; // 1 500 Б Анне — выше лимита
    private ReviewService service;

    @BeforeEach
    void setUp() {
        service = new ReviewService(new AccessPolicy(userRepository), operationRepository, accountRepository,
                reviewRepository, events, TestEntities.clock());
        pendingAccrual = TestEntities.operation(146, d.annaAccount, OperationType.ACCRUAL, OperationStatus.PENDING,
                OperationBasis.ACHIEVEMENT, 1500, TestEntities.at("2026-11-23", 12));
        for (AppUser u : new AppUser[] {d.managerUser, d.hrUser, d.financeUser}) {
            lenient().when(userRepository.findWithRoleById(u.getId())).thenReturn(Optional.of(u));
        }
        lenient().when(operationRepository.lockById(146L)).thenReturn(Optional.of(pendingAccrual));
        lenient().when(accountRepository.lockById(1L)).thenReturn(Optional.of(d.annaAccount));
    }

    /** Корректировка-отмена {@code original} на согласовании. */
    private Operation pendingCancellation(Operation original) {
        return Operation.create(new by.technosoft.bonus.domain.OperationNumber(200, "ОП-000200"), original.getAccount(),
                OperationType.CORRECTION, OperationStatus.PENDING, OperationBasis.CORRECTION, -original.getAmount(),
                "Начислено ошибочно", d.hrUser, TestEntities.at("2026-11-24", 10)).correcting(original);
    }

    @Test
    @DisplayName("FR5-3: подтверждённое финотделом начисление проводится и увеличивает баланс")
    void approvedAccrualUpdatesBalance() {
        OperationView view = service.approve(146L, FINANCE, "  Проверено  ");

        assertThat(view.status()).isEqualTo(OperationStatus.ACCRUED);
        assertThat(d.annaAccount.getBalance()).isEqualTo(10400);
        ArgumentCaptor<OperationReview> review = ArgumentCaptor.forClass(OperationReview.class);
        verify(reviewRepository).save(review.capture());
        assertThat(review.getValue().getDecision()).isEqualTo(ReviewDecision.APPROVED);
        assertThat(review.getValue().getComment()).isEqualTo("Проверено");
        assertThat(review.getValue().getReviewer()).isSameAs(d.financeUser);
    }

    @Test
    @DisplayName("FR5-3: заблокированное финотделом начисление баланс не меняет, причина обязательна")
    void blockedAccrualKeepsBalance() {
        assertThatThrownBy(() -> service.block(146L, FINANCE, "   "))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("Укажите причину");

        OperationView view = service.block(146L, FINANCE, "Дублирует начисление");

        assertThat(view.status()).isEqualTo(OperationStatus.BLOCKED);
        assertThat(d.annaAccount.getBalance()).isEqualTo(8900);
        ArgumentCaptor<OperationRegisteredEvent> event = ArgumentCaptor.forClass(OperationRegisteredEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().action()).isEqualTo(OperationAction.BLOCKING);
        assertThat(event.getValue().comment()).isEqualTo("Дублирует начисление");
    }

    @Test
    @DisplayName("FR5-1: подтверждать и блокировать начисления может только финотдел")
    void onlyFinanceReviews() {
        assertThatThrownBy(() -> service.approve(146L, 2L, null)).isInstanceOf(AccessDeniedDomainException.class);
        assertThatThrownBy(() -> service.block(146L, 20L, "Нет")).isInstanceOf(AccessDeniedDomainException.class);
        assertThatThrownBy(() -> service.pendingQueue(2L)).isInstanceOf(AccessDeniedDomainException.class);
        assertThat(pendingAccrual.getStatus()).isEqualTo(OperationStatus.PENDING);
    }

    @Test
    @DisplayName("FR5-3: повторное подтверждение уже проведённой операции запрещено")
    void secondApprovalIsRejected() {
        service.approve(146L, FINANCE, null);

        assertThatThrownBy(() -> service.approve(146L, FINANCE, null))
                .isInstanceOf(OperationStateException.class)
                .hasMessageContaining("«accrued»");
        assertThat(d.annaAccount.getBalance()).isEqualTo(10400);
    }

    @Test
    @DisplayName("FR5-3: подтверждение на заблокированный счёт невозможно")
    void approvalOnBlockedAccount() {
        Operation op = TestEntities.operation(147, d.pavelAccount, OperationType.ACCRUAL, OperationStatus.PENDING,
                OperationBasis.KPI, 1200, TestEntities.at("2026-11-23", 12));
        when(operationRepository.lockById(147L)).thenReturn(Optional.of(op));
        when(accountRepository.lockById(9L)).thenReturn(Optional.of(d.pavelAccount));

        assertThatThrownBy(() -> service.approve(147L, FINANCE, null)).isInstanceOf(AccountBlockedException.class);
    }

    @Test
    @DisplayName("FR7-2: подтверждённая отмена начисления пересчитывает баланс, исходная операция отменяется")
    void approvedCancellationRecalculatesBalance() {
        Operation original = TestEntities.operation(84, d.annaAccount, OperationType.ACCRUAL, OperationStatus.ACCRUED,
                OperationBasis.KPI, 1900, TestEntities.at("2026-11-01", 10));
        Operation cancellation = pendingCancellation(original);
        when(operationRepository.lockById(200L)).thenReturn(Optional.of(cancellation));
        when(operationRepository.lockById(84L)).thenReturn(Optional.of(original));

        OperationView view = service.approve(200L, FINANCE, null);

        assertThat(view.status()).isEqualTo(OperationStatus.CANCELLED);
        assertThat(original.getStatus()).isEqualTo(OperationStatus.CANCELLED);
        assertThat(d.annaAccount.getBalance()).isEqualTo(7000);
    }

    @Test
    @DisplayName("FR7-2: подтвердить корректировку, уводящую баланс в минус, нельзя — баланс не меняется")
    void approvalCannotMakeBalanceNegative() {
        BonusAccount poor = TestEntities.account(26, d.anna, 100);
        Operation original = TestEntities.operation(84, poor, OperationType.ACCRUAL, OperationStatus.ACCRUED,
                OperationBasis.KPI, 1000, TestEntities.at("2026-11-01", 10));
        Operation cancellation = pendingCancellation(original);
        when(operationRepository.lockById(200L)).thenReturn(Optional.of(cancellation));
        when(operationRepository.lockById(84L)).thenReturn(Optional.of(original));
        when(accountRepository.lockById(26L)).thenReturn(Optional.of(poor));

        assertThatThrownBy(() -> service.approve(200L, FINANCE, null))
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessageContaining("баланс станет −900\u00A0Б");
        assertThat(poor.getBalance()).isEqualTo(100);
        assertThat(original.getStatus()).isEqualTo(OperationStatus.ACCRUED);
        verify(reviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("FR7-2: корректировку уже изменённой исходной операции подтвердить нельзя")
    void approvalOfStaleCorrectionIsRejected() {
        Operation original = TestEntities.operation(84, d.annaAccount, OperationType.ACCRUAL,
                OperationStatus.CANCELLED, OperationBasis.KPI, 1900, TestEntities.at("2026-11-01", 10));
        Operation cancellation = pendingCancellation(original);
        when(operationRepository.lockById(200L)).thenReturn(Optional.of(cancellation));
        when(operationRepository.lockById(84L)).thenReturn(Optional.of(original));

        assertThatThrownBy(() -> service.approve(200L, FINANCE, null))
                .isInstanceOf(OperationStateException.class)
                .hasMessageContaining("уже изменена");
    }

    @Test
    @DisplayName("FR7-3: решение финотдела с комментарием длиннее 300 символов отклоняется")
    void commentLengthIsLimited() {
        assertThatThrownBy(() -> service.block(146L, FINANCE, "x".repeat(301)))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    @DisplayName("FR5-2: очередь финотдела содержит операции на согласовании")
    void pendingQueueForFinance() {
        when(operationRepository.findByStatusOrderByCreatedAtAsc(OperationStatus.PENDING))
                .thenReturn(List.of(pendingAccrual));

        assertThat(service.pendingQueue(FINANCE)).extracting(OperationView::number).containsExactly("ОП-000146");
    }
}
