package by.technosoft.bonus.service;

import java.time.Clock;
import java.time.OffsetDateTime;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import by.technosoft.bonus.domain.AppUser;
import by.technosoft.bonus.domain.BonusAccount;
import by.technosoft.bonus.domain.Operation;
import by.technosoft.bonus.domain.OperationBasis;
import by.technosoft.bonus.domain.OperationStatus;
import by.technosoft.bonus.domain.OperationType;
import by.technosoft.bonus.domain.RoleCode;
import by.technosoft.bonus.repository.BonusAccountRepository;
import by.technosoft.bonus.repository.OperationRepository;
import by.technosoft.bonus.service.dto.CorrectionAction;
import by.technosoft.bonus.service.dto.CorrectionRequest;
import by.technosoft.bonus.service.dto.CorrectionResult;
import by.technosoft.bonus.service.dto.OperationView;
import by.technosoft.bonus.service.event.OperationAction;
import by.technosoft.bonus.service.event.OperationRegisteredEvent;
import by.technosoft.bonus.service.exception.AccountBlockedException;
import by.technosoft.bonus.service.exception.DomainValidationException;
import by.technosoft.bonus.service.exception.ObjectNotFoundException;
import by.technosoft.bonus.service.exception.OperationStateException;
import jakarta.validation.Valid;

/**
 * Корректировка и отмена начислений (UC7). История не переписывается (DATA-1):
 * исходная операция переходит в «cancelled», а новая сумма фиксируется отдельной
 * операцией типа «correction» со ссылкой corrects_id и исходной суммой original_amount.
 * Отмена — корректировка с отрицательной суммой −исходная (в баланс не входит, статус «cancelled»).
 */
@Service
@Validated
public class CorrectionService {

    private final AccessPolicy accessPolicy;
    private final OperationRepository operationRepository;
    private final BonusAccountRepository accountRepository;
    private final OperationNumberGenerator numberGenerator;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public CorrectionService(AccessPolicy accessPolicy,
                             OperationRepository operationRepository,
                             BonusAccountRepository accountRepository,
                             OperationNumberGenerator numberGenerator,
                             ApplicationEventPublisher events,
                             Clock clock) {
        this.accessPolicy = accessPolicy;
        this.operationRepository = operationRepository;
        this.accountRepository = accountRepository;
        this.numberGenerator = numberGenerator;
        this.events = events;
        this.clock = clock;
    }

    /**
     * UC7 «Корректировка начисления» (увеличение, уменьшение или отмена; HR — любые операции,
     * руководитель — своей команды). Если итоговый баланс станет отрицательным, корректировка
     * уходит на согласование финотдела («pending»): исходная операция и баланс не меняются.
     */
    @Transactional
    public CorrectionResult correct(Long operationId, @Valid CorrectionRequest request, Long actorUserId) {
        AppUser actor = accessPolicy.requireActiveUser(actorUserId);
        accessPolicy.requireRole(actor, RoleCode.HR, RoleCode.MANAGER);

        Operation original = operationRepository.lockById(operationId)
                .orElseThrow(() -> new ObjectNotFoundException("Операция не найдена. Обновите журнал."));
        accessPolicy.requireCanManage(actor, original.getAccount().getEmployee());
        if (original.getType() == OperationType.WRITEOFF
                || (original.getStatus() != OperationStatus.ACCRUED && original.getStatus() != OperationStatus.CORRECTED)) {
            throw new OperationStateException("Операцию в статусе «" + original.getStatus().dbValue()
                    + "» корректировать нельзя.");
        }
        if (operationRepository.existsByCorrectsIdAndStatus(original.getId(), OperationStatus.PENDING)) {
            throw new OperationStateException("По операции " + original.getNumber()
                    + " уже есть корректировка на согласовании.");
        }

        boolean cancel = request.action() == CorrectionAction.CANCEL;
        int oldAmount = original.getAmount();
        int newAmount = cancel ? 0 : requireNewAmount(request, oldAmount);
        int delta = newAmount - oldAmount;

        BonusAccount account = accountRepository.lockById(original.getAccount().getId())
                .orElseThrow(() -> new ObjectNotFoundException("Счёт операции не найден."));
        if (!account.isActive()) {
            throw new AccountBlockedException("Счёт сотрудника заблокирован: корректировка невозможна.");
        }
        boolean needsApproval = account.getBalance() + delta < 0;

        OperationStatus status;
        if (needsApproval) {
            status = OperationStatus.PENDING;
        } else {
            status = cancel ? OperationStatus.CANCELLED : OperationStatus.CORRECTED;
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        Operation correction = Operation.create(numberGenerator.next(), account, OperationType.CORRECTION, status,
                        OperationBasis.CORRECTION, cancel ? -oldAmount : newAmount, request.reason().trim(), actor, now)
                .correcting(original);
        if (!needsApproval) {
            original.transitionTo(OperationStatus.CANCELLED);
            account.applyDelta(delta, now);
        }
        operationRepository.save(correction);

        String comment = needsApproval
                ? "Баланс станет " + PointsFormat.format(account.getBalance() + delta) + ": нужно согласование финотдела"
                : null;
        events.publishEvent(OperationRegisteredEvent.of(
                cancel ? OperationAction.CANCELLATION : OperationAction.CORRECTION, correction, actor, comment));
        return new CorrectionResult(OperationView.of(original), OperationView.of(correction), needsApproval);
    }

    private static int requireNewAmount(CorrectionRequest request, int oldAmount) {
        Integer newAmount = request.newAmount();
        if (newAmount == null || newAmount <= 0) {
            throw new DomainValidationException(
                    "Сумма должна быть больше нуля. Для отмены выберите «Отменить начисление».");
        }
        if (newAmount == oldAmount) {
            throw new DomainValidationException("Новая сумма совпадает с текущей — корректировать нечего.");
        }
        return newAmount;
    }
}
