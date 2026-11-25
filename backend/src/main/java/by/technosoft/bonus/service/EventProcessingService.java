package by.technosoft.bonus.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import by.technosoft.bonus.config.BonusProperties;
import by.technosoft.bonus.domain.AccrualRule;
import by.technosoft.bonus.domain.BonusAccount;
import by.technosoft.bonus.domain.BusinessEvent;
import by.technosoft.bonus.domain.EventStatus;
import by.technosoft.bonus.domain.Operation;
import by.technosoft.bonus.domain.OperationBasis;
import by.technosoft.bonus.domain.OperationStatus;
import by.technosoft.bonus.domain.OperationType;
import by.technosoft.bonus.repository.AccrualRuleRepository;
import by.technosoft.bonus.repository.BonusAccountRepository;
import by.technosoft.bonus.repository.BusinessEventRepository;
import by.technosoft.bonus.repository.CampaignRepository;
import by.technosoft.bonus.repository.OperationRepository;
import by.technosoft.bonus.service.dto.BatchProcessingResult;
import by.technosoft.bonus.service.dto.CalculationRequest;
import by.technosoft.bonus.service.dto.CalculationResult;
import by.technosoft.bonus.service.dto.EventOutcome;
import by.technosoft.bonus.service.dto.EventProcessingResult;
import by.technosoft.bonus.service.event.EventProcessingFailedEvent;
import by.technosoft.bonus.service.event.OperationAction;
import by.technosoft.bonus.service.event.OperationRegisteredEvent;
import by.technosoft.bonus.service.exception.AccountBlockedException;
import by.technosoft.bonus.service.exception.BonusDomainException;
import by.technosoft.bonus.service.exception.InvalidCalculationInputException;
import by.technosoft.bonus.service.exception.ObjectNotFoundException;

/**
 * Автоматическое начисление по бизнес-событиям (UC4, BR-2): событие из очереди
 * business_event → правило по показателю → расчёт (UC6) → операция «accrued» на счёт.
 */
@Service
public class EventProcessingService {

    private static final Logger log = LoggerFactory.getLogger(EventProcessingService.class);

    /** Событие закрытия KPI: код показателя и значение приходят в payload. */
    static final String KPI_CLOSED = "kpi_closed";

    /** Прочие типы событий и показатель, к правилу которого они относятся. */
    static final Map<String, String> EVENT_TYPE_KPI = Map.of(
            "anniversary", "TENURE",
            "stage_accepted", "STAGE",
            "certificate_obtained", "CERT");

    private final BusinessEventRepository eventRepository;
    private final OperationRepository operationRepository;
    private final AccrualRuleRepository ruleRepository;
    private final BonusAccountRepository accountRepository;
    private final CampaignRepository campaignRepository;
    private final BonusCalculationService calculationService;
    private final OperationNumberGenerator numberGenerator;
    private final ApplicationEventPublisher events;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final BonusProperties properties;
    private final Clock clock;

    public EventProcessingService(BusinessEventRepository eventRepository,
                                  OperationRepository operationRepository,
                                  AccrualRuleRepository ruleRepository,
                                  BonusAccountRepository accountRepository,
                                  CampaignRepository campaignRepository,
                                  BonusCalculationService calculationService,
                                  OperationNumberGenerator numberGenerator,
                                  ApplicationEventPublisher events,
                                  ObjectMapper objectMapper,
                                  PlatformTransactionManager transactionManager,
                                  BonusProperties properties,
                                  Clock clock) {
        this.eventRepository = eventRepository;
        this.operationRepository = operationRepository;
        this.ruleRepository = ruleRepository;
        this.accountRepository = accountRepository;
        this.campaignRepository = campaignRepository;
        this.calculationService = calculationService;
        this.numberGenerator = numberGenerator;
        this.events = events;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * UC4 «Автоначисление» по одному событию. Идемпотентно (NFR-4): событие блокируется,
     * и если оно уже обработано или по нему есть операция, ничего не делается.
     * Ошибка входных данных или заблокированный счёт → событие «failed» с error_message
     * (отложено, альт. поток UC4), начисление не создаётся. KPI ниже порога → «processed» без начисления (BR-5).
     */
    @Transactional
    public EventProcessingResult process(Long eventId) {
        BusinessEvent event = eventRepository.lockById(eventId)
                .orElseThrow(() -> new ObjectNotFoundException("Событие " + eventId + " не найдено"));
        if (event.getStatus() == EventStatus.PROCESSED || operationRepository.existsByEventId(eventId)) {
            return new EventProcessingResult(eventId, EventOutcome.SKIPPED, null, "Событие уже обработано");
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        try {
            Long employeeId = event.getEmployee().getId();
            EventSignal signal = parse(event);
            LocalDate businessDate = event.getReceivedAt().atZoneSameInstant(properties.zone()).toLocalDate();
            AccrualRule rule = ruleRepository.findApplicableByKpiCode(signal.kpiCode(), businessDate).stream()
                    .findFirst()
                    .orElseThrow(() -> new InvalidCalculationInputException(
                            "Нет действующего правила для показателя " + signal.kpiCode()));
            CalculationResult calc = calculationService.calculate(
                    new CalculationRequest(employeeId, rule.getId(), signal.value(), businessDate));
            if (calc.isZero()) {
                event.markProcessed(now);
                return new EventProcessingResult(eventId, EventOutcome.NO_BONUS, null, calc.formula());
            }

            BonusAccount account = accountRepository.lockByEmployeeId(employeeId)
                    .orElseThrow(() -> new InvalidCalculationInputException("У сотрудника нет бонусного счёта"));
            if (!account.isActive()) {
                throw new AccountBlockedException("Счёт сотрудника заблокирован: начисление отложено");
            }
            Operation op = Operation.create(numberGenerator.next(), account, OperationType.ACCRUAL,
                            OperationStatus.ACCRUED, OperationBasis.KPI, calc.total(), reason(rule, signal), null, now)
                    .withRule(rule)
                    .withCampaign(calc.campaignId() == null ? null : campaignRepository.getReferenceById(calc.campaignId()))
                    .withEvent(event);
            account.applyDelta(calc.total(), now);
            operationRepository.save(op);
            event.markProcessed(now);
            events.publishEvent(OperationRegisteredEvent.of(OperationAction.AUTO_ACCRUAL, op, null, calc.formula()));
            return new EventProcessingResult(eventId, EventOutcome.ACCRUED, op.getId(), calc.formula());
        } catch (BonusDomainException ex) {
            event.markFailed(ex.getMessage());
            events.publishEvent(new EventProcessingFailedEvent(eventId, event.getEventType(),
                    event.getEmployee().getId(), ex.getMessage()));
            log.warn("Событие {} отложено: {}", eventId, ex.getMessage());
            return new EventProcessingResult(eventId, EventOutcome.FAILED, null, ex.getMessage());
        }
    }

    /**
     * UC4, пакетный режим: обрабатывает необработанные события («new») в порядке поступления.
     * Каждое событие — в своей транзакции, поэтому сбой одного не отменяет остальные;
     * непредвиденная ошибка фиксируется как «failed», и обработка пакета продолжается.
     */
    public BatchProcessingResult processPending() {
        List<Long> ids = transactionTemplate.execute(status -> eventRepository.findIdsByStatus(
                EventStatus.NEW, PageRequest.of(0, properties.eventBatchSize())));
        int accrued = 0;
        int noBonus = 0;
        int skipped = 0;
        int failed = 0;
        for (Long id : ids) {
            EventOutcome outcome;
            try {
                outcome = transactionTemplate.execute(status -> process(id)).outcome();
            } catch (RuntimeException ex) {
                log.error("Непредвиденная ошибка обработки события {}", id, ex);
                markFailedSafely(id, ex);
                outcome = EventOutcome.FAILED;
            }
            switch (outcome) {
                case ACCRUED -> accrued++;
                case NO_BONUS -> noBonus++;
                case SKIPPED -> skipped++;
                case FAILED -> failed++;
            }
        }
        return new BatchProcessingResult(ids.size(), accrued, noBonus, skipped, failed);
    }

    /** Фиксирует непредвиденный сбой в отдельной транзакции; ошибка фиксации только логируется. */
    private void markFailedSafely(Long eventId, RuntimeException cause) {
        try {
            transactionTemplate.executeWithoutResult(status -> eventRepository.findById(eventId)
                    .filter(e -> e.getStatus() == EventStatus.NEW)
                    .ifPresent(e -> e.markFailed("Внутренняя ошибка обработки: " + cause.getClass().getSimpleName())));
        } catch (RuntimeException ex) {
            log.error("Не удалось отметить событие {} как failed", eventId, ex);
        }
    }

    /** Извлекает из события код показателя и значение KPI. */
    EventSignal parse(BusinessEvent event) {
        JsonNode payload;
        try {
            payload = objectMapper.readTree(event.getPayload());
        } catch (JsonProcessingException e) {
            throw new InvalidCalculationInputException("Некорректные входные данные: payload события не является JSON");
        }
        String kpiCode;
        if (KPI_CLOSED.equals(event.getEventType())) {
            kpiCode = payload.path("kpi").textValue();
            if (kpiCode == null || kpiCode.isBlank()) {
                throw new InvalidCalculationInputException("Некорректные входные данные: не указан показатель KPI");
            }
        } else {
            kpiCode = EVENT_TYPE_KPI.get(event.getEventType());
            if (kpiCode == null) {
                throw new InvalidCalculationInputException("Неизвестный тип события: " + event.getEventType());
            }
        }
        JsonNode valueNode = payload.path("value");
        BigDecimal value = null;
        if (valueNode.isNumber()) {
            value = valueNode.decimalValue();
        } else if (!valueNode.isMissingNode() && !valueNode.isNull()) {
            throw new InvalidCalculationInputException("Некорректные входные данные: значение KPI не является числом");
        }
        return new EventSignal(kpiCode, value);
    }

    private static String reason(AccrualRule rule, EventSignal signal) {
        if (signal.value() == null) {
            return rule.getName();
        }
        return rule.getName() + ", KPI " + signal.value().stripTrailingZeros().toPlainString() + " %";
    }

    /**
     * Данные события, нужные для расчёта.
     *
     * @param kpiCode код показателя, по которому ищется правило
     * @param value   уровень достижения KPI, % ({@code null} для событий без значения)
     */
    record EventSignal(String kpiCode, BigDecimal value) {
    }
}
