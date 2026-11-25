package by.technosoft.bonus.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import by.technosoft.bonus.config.BonusProperties;
import by.technosoft.bonus.domain.AccrualRule;
import by.technosoft.bonus.domain.AppUser;
import by.technosoft.bonus.domain.BonusAccount;
import by.technosoft.bonus.domain.Employee;
import by.technosoft.bonus.domain.Operation;
import by.technosoft.bonus.domain.OperationBasis;
import by.technosoft.bonus.domain.OperationStatus;
import by.technosoft.bonus.domain.OperationType;
import by.technosoft.bonus.domain.RoleCode;
import by.technosoft.bonus.repository.AccrualRuleRepository;
import by.technosoft.bonus.repository.BonusAccountRepository;
import by.technosoft.bonus.repository.CampaignRepository;
import by.technosoft.bonus.repository.EmployeeRepository;
import by.technosoft.bonus.repository.OperationRepository;
import by.technosoft.bonus.service.dto.AccrualPreview;
import by.technosoft.bonus.service.dto.AccrualPreviewLine;
import by.technosoft.bonus.service.dto.AccrualRequest;
import by.technosoft.bonus.service.dto.AccrualResult;
import by.technosoft.bonus.service.dto.CalculationResult;
import by.technosoft.bonus.service.dto.OperationView;
import by.technosoft.bonus.service.event.OperationAction;
import by.technosoft.bonus.service.event.OperationRegisteredEvent;
import by.technosoft.bonus.service.exception.AccountBlockedException;
import by.technosoft.bonus.service.exception.DomainValidationException;
import by.technosoft.bonus.service.exception.ObjectNotFoundException;
import jakarta.validation.Valid;

/**
 * Ручное начисление баллов руководителем или HR (UC5) с расчётом суммы (UC6).
 */
@Service
@Validated
public class AccrualService {

    /** Основания, допустимые для ручного начисления. */
    private static final Set<OperationBasis> MANUAL_BASES =
            EnumSet.of(OperationBasis.KPI, OperationBasis.PROJECT, OperationBasis.ACHIEVEMENT);

    private final AccessPolicy accessPolicy;
    private final EmployeeRepository employeeRepository;
    private final BonusAccountRepository accountRepository;
    private final OperationRepository operationRepository;
    private final AccrualRuleRepository ruleRepository;
    private final CampaignRepository campaignRepository;
    private final BonusCalculationService calculationService;
    private final OperationNumberGenerator numberGenerator;
    private final ApplicationEventPublisher events;
    private final BonusProperties properties;
    private final Clock clock;

    public AccrualService(AccessPolicy accessPolicy,
                          EmployeeRepository employeeRepository,
                          BonusAccountRepository accountRepository,
                          OperationRepository operationRepository,
                          AccrualRuleRepository ruleRepository,
                          CampaignRepository campaignRepository,
                          BonusCalculationService calculationService,
                          OperationNumberGenerator numberGenerator,
                          ApplicationEventPublisher events,
                          BonusProperties properties,
                          Clock clock) {
        this.accessPolicy = accessPolicy;
        this.employeeRepository = employeeRepository;
        this.accountRepository = accountRepository;
        this.operationRepository = operationRepository;
        this.ruleRepository = ruleRepository;
        this.campaignRepository = campaignRepository;
        this.calculationService = calculationService;
        this.numberGenerator = numberGenerator;
        this.events = events;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * UC5, шаг «Предварительный расчёт» (UC6): сумма × коэффициент кампании по каждому сотруднику
     * и признак превышения лимита. Ничего не записывает.
     */
    @Transactional(readOnly = true)
    public AccrualPreview preview(Long actorUserId, @Valid AccrualRequest request) {
        AppUser actor = accessPolicy.requireActiveUser(actorUserId);
        accessPolicy.requireRole(actor, RoleCode.MANAGER, RoleCode.HR);
        validate(request);
        return buildPreview(actor, request, LocalDate.now(clock));
    }

    /**
     * UC5 «Ручное начисление»: руководитель — только своей команде, HR — любому сотруднику (SEC-3).
     * Итог не выше лимита — операция «accrued» и рост баланса; выше лимита — «pending»
     * на согласование финотдела без изменения баланса. Заблокированный счёт любого получателя
     * отменяет всё начисление (операция атомарна).
     */
    @Transactional
    public AccrualResult create(Long actorUserId, @Valid AccrualRequest request) {
        AppUser actor = accessPolicy.requireActiveUser(actorUserId);
        accessPolicy.requireRole(actor, RoleCode.MANAGER, RoleCode.HR);
        validate(request);
        AccrualPreview preview = buildPreview(actor, request, LocalDate.now(clock));

        // Блокируем счета в порядке id сотрудников (строки preview отсортированы) — без взаимных блокировок.
        List<BonusAccount> accounts = new ArrayList<>();
        for (AccrualPreviewLine line : preview.lines()) {
            BonusAccount account = accountRepository.lockByEmployeeId(line.employeeId())
                    .orElseThrow(() -> new ObjectNotFoundException(
                            "У сотрудника «" + line.employeeName() + "» нет бонусного счёта"));
            if (!account.isActive()) {
                throw new AccountBlockedException(
                        "Счёт сотрудника «" + line.employeeName() + "» заблокирован: начисление невозможно");
            }
            accounts.add(account);
        }

        AccrualRule rule = request.ruleId() == null ? null : ruleRepository.findById(request.ruleId())
                .orElseThrow(() -> new ObjectNotFoundException("Правило " + request.ruleId() + " не найдено"));
        String reason = request.reason().trim();
        OffsetDateTime now = OffsetDateTime.now(clock);
        String limitComment = "Превышен лимит " + PointsFormat.format(preview.limit()) + " на сотрудника";

        List<OperationView> created = new ArrayList<>();
        for (int i = 0; i < preview.lines().size(); i++) {
            AccrualPreviewLine line = preview.lines().get(i);
            BonusAccount account = accounts.get(i);
            OperationStatus status = line.needsApproval() ? OperationStatus.PENDING : OperationStatus.ACCRUED;
            Operation op = Operation.create(numberGenerator.next(), account, OperationType.ACCRUAL, status,
                            request.basis(), line.total(), reason, actor, now)
                    .withRule(rule)
                    .withCampaign(line.campaignId() == null ? null : campaignRepository.getReferenceById(line.campaignId()));
            if (status == OperationStatus.ACCRUED) {
                account.applyDelta(line.total(), now);
            }
            operationRepository.save(op);
            events.publishEvent(OperationRegisteredEvent.of(OperationAction.MANUAL_ACCRUAL, op, actor,
                    line.needsApproval() ? limitComment : null));
            created.add(OperationView.of(op));
        }
        int pending = (int) created.stream().filter(o -> o.status() == OperationStatus.PENDING).count();
        return new AccrualResult(created, created.size() - pending, pending);
    }

    /** Бизнес-проверки запроса сверх аннотаций Bean Validation. */
    private void validate(AccrualRequest request) {
        if (!MANUAL_BASES.contains(request.basis())) {
            throw new DomainValidationException("Для ручного начисления выберите основание: KPI, проект или достижение.");
        }
        if (request.reason().isBlank()) {
            throw new DomainValidationException("Укажите сумму больше нуля и причину начисления.");
        }
    }

    /** Расчёт строк: сотрудники без повторов в порядке id, проверка прав на каждого. */
    private AccrualPreview buildPreview(AppUser actor, AccrualRequest request, LocalDate today) {
        int limit = properties.accrualLimit();
        List<AccrualPreviewLine> lines = new ArrayList<>();
        for (Long employeeId : new TreeSet<>(request.employeeIds())) {
            Employee employee = employeeRepository.findById(employeeId)
                    .orElseThrow(() -> new DomainValidationException(
                            "В списке есть неизвестный сотрудник. Вернитесь к шагу 1."));
            accessPolicy.requireCanManage(actor, employee);
            CalculationResult calc = calculationService.calculateManual(
                    employeeId, request.amount(), request.campaignId(), today);
            lines.add(new AccrualPreviewLine(employeeId, employee.getFullName(), request.amount(),
                    calc.coefficient(), calc.campaignId(), calc.total(), calc.total() > limit));
        }
        int total = lines.stream().mapToInt(AccrualPreviewLine::total).sum();
        int approvalCount = (int) lines.stream().filter(AccrualPreviewLine::needsApproval).count();
        return new AccrualPreview(List.copyOf(lines), total, limit, approvalCount);
    }
}
