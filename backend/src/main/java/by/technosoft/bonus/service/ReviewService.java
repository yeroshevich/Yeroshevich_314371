package by.technosoft.bonus.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import by.technosoft.bonus.domain.AppUser;
import by.technosoft.bonus.domain.BonusAccount;
import by.technosoft.bonus.domain.Operation;
import by.technosoft.bonus.domain.OperationReview;
import by.technosoft.bonus.domain.OperationStatus;
import by.technosoft.bonus.domain.OperationType;
import by.technosoft.bonus.domain.ReviewDecision;
import by.technosoft.bonus.domain.RoleCode;
import by.technosoft.bonus.repository.BonusAccountRepository;
import by.technosoft.bonus.repository.OperationRepository;
import by.technosoft.bonus.repository.OperationReviewRepository;
import by.technosoft.bonus.service.dto.OperationView;
import by.technosoft.bonus.service.event.OperationAction;
import by.technosoft.bonus.service.event.OperationRegisteredEvent;
import by.technosoft.bonus.service.exception.AccountBlockedException;
import by.technosoft.bonus.service.exception.DomainValidationException;
import by.technosoft.bonus.service.exception.InsufficientBalanceException;
import by.technosoft.bonus.service.exception.ObjectNotFoundException;
import by.technosoft.bonus.service.exception.OperationStateException;

/**
 * Контроль финансового отдела: подтверждение и блокировка операций,
 * ушедших на согласование (превышение лимита UC5, отрицательный баланс UC7).
 */
@Service
public class ReviewService {

    private static final int COMMENT_LENGTH = 300;

    private final AccessPolicy accessPolicy;
    private final OperationRepository operationRepository;
    private final BonusAccountRepository accountRepository;
    private final OperationReviewRepository reviewRepository;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ReviewService(AccessPolicy accessPolicy,
                         OperationRepository operationRepository,
                         BonusAccountRepository accountRepository,
                         OperationReviewRepository reviewRepository,
                         ApplicationEventPublisher events,
                         Clock clock) {
        this.accessPolicy = accessPolicy;
        this.operationRepository = operationRepository;
        this.accountRepository = accountRepository;
        this.reviewRepository = reviewRepository;
        this.events = events;
        this.clock = clock;
    }

    /** Контроль финотдела: очередь операций на согласовании, старые сверху. */
    @Transactional(readOnly = true)
    public List<OperationView> pendingQueue(Long reviewerUserId) {
        AppUser reviewer = accessPolicy.requireActiveUser(reviewerUserId);
        accessPolicy.requireRole(reviewer, RoleCode.FINANCE);
        return operationRepository.findByStatusOrderByCreatedAtAsc(OperationStatus.PENDING).stream()
                .map(OperationView::of)
                .toList();
    }

    /**
     * Контроль финотдела, «Подтвердить»: только операции «pending».
     * Начисление → «accrued» и рост баланса; корректировка → «corrected» (отмена → «cancelled»),
     * исходная операция → «cancelled», баланс меняется на разницу сумм. Решение пишется в operation_review.
     */
    @Transactional
    public OperationView approve(Long operationId, Long reviewerUserId, String comment) {
        AppUser reviewer = accessPolicy.requireActiveUser(reviewerUserId);
        accessPolicy.requireRole(reviewer, RoleCode.FINANCE);
        Operation op = lockPending(operationId, "Подтвердить");

        OffsetDateTime now = OffsetDateTime.now(clock);
        if (op.getType() == OperationType.CORRECTION) {
            Operation original = operationRepository.lockById(op.getCorrects().getId())
                    .orElseThrow(() -> new ObjectNotFoundException("Корректируемая операция не найдена."));
            if (original.getStatus() != OperationStatus.ACCRUED && original.getStatus() != OperationStatus.CORRECTED) {
                throw new OperationStateException("Исходная операция " + original.getNumber()
                        + " уже изменена — заблокируйте корректировку.");
            }
            BonusAccount account = lockActiveAccount(op);
            int delta = op.correctionDelta();
            if (account.getBalance() + delta < 0) {
                throw new InsufficientBalanceException(account.getBalance(), -delta,
                        "Подтверждение невозможно: баланс станет " + PointsFormat.format(account.getBalance() + delta)
                                + ". Заблокируйте корректировку или дождитесь пополнения счёта.");
            }
            original.transitionTo(OperationStatus.CANCELLED);
            op.transitionTo(op.isCancellationRequest() ? OperationStatus.CANCELLED : OperationStatus.CORRECTED);
            account.applyDelta(delta, now);
        } else {
            BonusAccount account = lockActiveAccount(op);
            op.transitionTo(OperationStatus.ACCRUED);
            account.applyDelta(op.getAmount(), now);
        }
        String trimmed = normalizeComment(comment);
        reviewRepository.save(new OperationReview(op, reviewer, ReviewDecision.APPROVED, trimmed, now));
        events.publishEvent(OperationRegisteredEvent.of(OperationAction.APPROVAL, op, reviewer, trimmed));
        return OperationView.of(op);
    }

    /**
     * Контроль финотдела, «Заблокировать»: только операции «pending» → «blocked»,
     * баланс не меняется, причина обязательна (её увидит сотрудник). Решение пишется в operation_review.
     */
    @Transactional
    public OperationView block(Long operationId, Long reviewerUserId, String comment) {
        AppUser reviewer = accessPolicy.requireActiveUser(reviewerUserId);
        accessPolicy.requireRole(reviewer, RoleCode.FINANCE);
        String reason = normalizeComment(comment);
        if (reason == null) {
            throw new DomainValidationException("Укажите причину блокировки — её увидит сотрудник.");
        }
        Operation op = lockPending(operationId, "Заблокировать");
        OffsetDateTime now = OffsetDateTime.now(clock);
        op.transitionTo(OperationStatus.BLOCKED);
        reviewRepository.save(new OperationReview(op, reviewer, ReviewDecision.BLOCKED, reason, now));
        events.publishEvent(OperationRegisteredEvent.of(OperationAction.BLOCKING, op, reviewer, reason));
        return OperationView.of(op);
    }

    /** Пустой комментарий — {@code null}; длина ограничена схемой (300 символов). */
    private static String normalizeComment(String comment) {
        if (comment == null || comment.isBlank()) {
            return null;
        }
        String trimmed = comment.trim();
        if (trimmed.length() > COMMENT_LENGTH) {
            throw new DomainValidationException("Комментарий длиннее " + COMMENT_LENGTH + " символов.");
        }
        return trimmed;
    }

    private Operation lockPending(Long operationId, String action) {
        Operation op = operationRepository.lockById(operationId)
                .orElseThrow(() -> new ObjectNotFoundException("Операция не найдена. Обновите очередь."));
        if (op.getStatus() != OperationStatus.PENDING) {
            throw new OperationStateException(action + " можно только операцию на согласовании; статус "
                    + op.getNumber() + " — «" + op.getStatus().dbValue() + "».");
        }
        return op;
    }

    private BonusAccount lockActiveAccount(Operation op) {
        BonusAccount account = accountRepository.lockById(op.getAccount().getId())
                .orElseThrow(() -> new ObjectNotFoundException("Счёт операции не найден."));
        if (!account.isActive()) {
            throw new AccountBlockedException("Счёт сотрудника заблокирован: подтверждение невозможно.");
        }
        return account;
    }
}
