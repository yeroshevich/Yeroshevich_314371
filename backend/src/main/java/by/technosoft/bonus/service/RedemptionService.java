package by.technosoft.bonus.service;

import java.time.Clock;
import java.time.OffsetDateTime;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import by.technosoft.bonus.domain.AppUser;
import by.technosoft.bonus.domain.BonusAccount;
import by.technosoft.bonus.domain.Operation;
import by.technosoft.bonus.domain.OperationBasis;
import by.technosoft.bonus.domain.OperationStatus;
import by.technosoft.bonus.domain.OperationType;
import by.technosoft.bonus.domain.Program;
import by.technosoft.bonus.repository.AppUserRepository;
import by.technosoft.bonus.repository.BonusAccountRepository;
import by.technosoft.bonus.repository.OperationRepository;
import by.technosoft.bonus.repository.ProgramRepository;
import by.technosoft.bonus.service.dto.OperationView;
import by.technosoft.bonus.service.dto.RedemptionResult;
import by.technosoft.bonus.service.event.OperationAction;
import by.technosoft.bonus.service.event.OperationRegisteredEvent;
import by.technosoft.bonus.service.exception.AccountBlockedException;
import by.technosoft.bonus.service.exception.InsufficientBalanceException;
import by.technosoft.bonus.service.exception.ObjectNotFoundException;
import by.technosoft.bonus.service.exception.ProgramUnavailableException;

/**
 * Использование баллов на программы мотивации (UC3). Баллы тратятся только внутри
 * каталога программ — конвертации в зарплату нет (BR-3).
 */
@Service
public class RedemptionService {

    private final ProgramRepository programRepository;
    private final BonusAccountRepository accountRepository;
    private final AppUserRepository userRepository;
    private final OperationRepository operationRepository;
    private final OperationNumberGenerator numberGenerator;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public RedemptionService(ProgramRepository programRepository,
                             BonusAccountRepository accountRepository,
                             AppUserRepository userRepository,
                             OperationRepository operationRepository,
                             OperationNumberGenerator numberGenerator,
                             ApplicationEventPublisher events,
                             Clock clock) {
        this.programRepository = programRepository;
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.operationRepository = operationRepository;
        this.numberGenerator = numberGenerator;
        this.events = events;
        this.clock = clock;
    }

    /**
     * UC3 «Использование бонусов»: программа активна, счёт активен, баланс ≥ стоимости.
     * Счёт блокируется (SELECT … FOR UPDATE) до конца транзакции, поэтому два параллельных
     * списания не уведут баланс в минус; создаётся операция «written_off» с суммой −стоимость.
     */
    @Transactional
    public RedemptionResult redeem(Long employeeId, Long programId) {
        Program program = programRepository.findById(programId)
                .orElseThrow(() -> new ObjectNotFoundException("Программа не найдена. Обновите каталог."));
        if (!program.isActive()) {
            String why = program.getInactiveReason() == null ? "" : " " + program.getInactiveReason();
            throw new ProgramUnavailableException("Программа «" + program.getName() + "» сейчас недоступна." + why);
        }
        BonusAccount account = accountRepository.lockByEmployeeId(employeeId)
                .orElseThrow(() -> new ObjectNotFoundException("К учётной записи не привязан бонусный счёт."));
        if (!account.isActive()) {
            throw new AccountBlockedException("Баланс недоступен, обратитесь к HR.");
        }
        int cost = program.getCost();
        if (account.getBalance() < cost) {
            throw new InsufficientBalanceException(account.getBalance(), cost,
                    "Не хватает " + PointsFormat.format(cost - account.getBalance()) + ": программа стоит "
                            + PointsFormat.format(cost) + ", на счёте " + PointsFormat.format(account.getBalance()) + ".");
        }

        AppUser owner = userRepository.findByEmployeeId(employeeId).orElse(null);
        OffsetDateTime now = OffsetDateTime.now(clock);
        Operation op = Operation.create(numberGenerator.next(), account, OperationType.WRITEOFF,
                        OperationStatus.WRITTEN_OFF, OperationBasis.PROGRAM, -cost,
                        "Программа «" + program.getName() + "»", owner, now)
                .withProgram(program);
        account.applyDelta(-cost, now);
        operationRepository.save(op);
        events.publishEvent(OperationRegisteredEvent.of(OperationAction.REDEMPTION, op, owner, null));
        return new RedemptionResult(OperationView.of(op), account.getBalance());
    }
}
