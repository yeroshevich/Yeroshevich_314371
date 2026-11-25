package by.technosoft.bonus.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import by.technosoft.bonus.domain.AccrualRule;
import by.technosoft.bonus.domain.Campaign;
import by.technosoft.bonus.domain.CampaignStatus;
import by.technosoft.bonus.domain.Employee;
import by.technosoft.bonus.domain.OperationStatus;
import by.technosoft.bonus.repository.AccrualRuleRepository;
import by.technosoft.bonus.repository.CampaignDepartmentRepository;
import by.technosoft.bonus.repository.CampaignRepository;
import by.technosoft.bonus.repository.EmployeeRepository;
import by.technosoft.bonus.repository.OperationRepository;
import by.technosoft.bonus.service.dto.CalculationRequest;
import by.technosoft.bonus.service.dto.CalculationResult;
import by.technosoft.bonus.service.exception.BonusDomainException;
import by.technosoft.bonus.service.exception.InvalidCalculationInputException;
import by.technosoft.bonus.service.strategy.AccrualStrategy;
import by.technosoft.bonus.service.strategy.AccrualStrategyRegistry;
import by.technosoft.bonus.service.strategy.BaseAmount;

/**
 * Сервис расчёта начислений (UC6). Координирует валидацию входных данных,
 * применение правила (через {@link AccrualStrategy}) и учёт бонусных кампаний.
 * Сам ничего не записывает: результат — объект-значение {@link CalculationResult}.
 * <p>
 * Доменные исключения не помечают внешнюю транзакцию на откат ({@code noRollbackFor}):
 * вызывающий сервис может перехватить ошибку расчёта и зафиксировать её (UC4).
 */
@Service
@Transactional(readOnly = true, noRollbackFor = BonusDomainException.class)
public class BonusCalculationService {

    /** Шаг округления по умолчанию, если в params правила нет roundTo. */
    static final int DEFAULT_ROUND_TO = 50;

    /** Статусы операций, которые расходуют бюджет кампании (pending — резерв). */
    private static final Set<OperationStatus> BUDGET_STATUSES =
            EnumSet.of(OperationStatus.ACCRUED, OperationStatus.CORRECTED, OperationStatus.PENDING);

    private final EmployeeRepository employeeRepository;
    private final AccrualRuleRepository ruleRepository;
    private final CampaignRepository campaignRepository;
    private final CampaignDepartmentRepository campaignDepartmentRepository;
    private final OperationRepository operationRepository;
    private final AccrualStrategyRegistry strategyRegistry;
    private final ObjectMapper objectMapper;

    public BonusCalculationService(EmployeeRepository employeeRepository,
                                   AccrualRuleRepository ruleRepository,
                                   CampaignRepository campaignRepository,
                                   CampaignDepartmentRepository campaignDepartmentRepository,
                                   OperationRepository operationRepository,
                                   AccrualStrategyRegistry strategyRegistry,
                                   ObjectMapper objectMapper) {
        this.employeeRepository = employeeRepository;
        this.ruleRepository = ruleRepository;
        this.campaignRepository = campaignRepository;
        this.campaignDepartmentRepository = campaignDepartmentRepository;
        this.operationRepository = operationRepository;
        this.strategyRegistry = strategyRegistry;
        this.objectMapper = objectMapper;
    }

    /**
     * UC6 «Расчёт бонуса» по правилу (BR-4, BR-5): валидация входных данных → стратегия правила →
     * коэффициент активной кампании подразделения → округление до params.roundTo.
     * Некорректные входные данные — {@link InvalidCalculationInputException}, начисление не производится.
     */
    public CalculationResult calculate(CalculationRequest request) {
        if (request == null || request.employeeId() == null || request.ruleId() == null || request.onDate() == null) {
            throw new InvalidCalculationInputException(
                    "Некорректные входные данные: не указаны сотрудник, правило или дата расчёта");
        }
        Employee employee = employeeRepository.findById(request.employeeId())
                .orElseThrow(() -> new InvalidCalculationInputException(
                        "Некорректные входные данные: сотрудник " + request.employeeId() + " не найден"));
        AccrualRule rule = ruleRepository.findById(request.ruleId())
                .orElseThrow(() -> new InvalidCalculationInputException(
                        "Некорректные входные данные: правило " + request.ruleId() + " не найдено"));
        if (!rule.isApplicableOn(request.onDate())) {
            throw new InvalidCalculationInputException(
                    "Правило «" + rule.getName() + "» не действует на " + request.onDate());
        }

        AccrualStrategy strategy = strategyRegistry.forRule(rule);
        BaseAmount base = strategy.calculate(rule, request.kpiValue());
        if (base.isZero()) {
            return new CalculationResult(employee.getId(), rule.getId(), BigDecimal.ZERO, BigDecimal.ONE, null, 0,
                    base.formula());
        }

        int roundTo = roundTo(rule);
        List<Campaign> campaigns = campaignRepository.findRunningForDepartment(
                employee.getDepartment().getId(), CampaignStatus.ACTIVE, request.onDate());
        for (Campaign campaign : campaigns) {
            int total = roundToStep(base.value().multiply(campaign.getCoefficient()), roundTo);
            if (fitsBudget(campaign, total)) {
                String formula = base.formula() + "; × " + campaign.getCoefficient().toPlainString()
                        + " (кампания «" + campaign.getName() + "»); округление до " + roundTo + " → " + total;
                return new CalculationResult(employee.getId(), rule.getId(), base.value(),
                        campaign.getCoefficient(), campaign.getId(), total, formula);
            }
        }
        int total = roundToStep(base.value(), roundTo);
        return new CalculationResult(employee.getId(), rule.getId(), base.value(), BigDecimal.ONE, null, total,
                base.formula() + "; округление до " + roundTo + " → " + total);
    }

    /**
     * UC5/UC6: расчёт ручного начисления — сумма × коэффициент выбранной кампании,
     * если она активна на дату, охватывает подразделение сотрудника и её бюджет не исчерпан.
     * Округление — до целого балла.
     */
    public CalculationResult calculateManual(Long employeeId, int amount, Long campaignId, LocalDate onDate) {
        if (amount <= 0) {
            throw new InvalidCalculationInputException("Некорректные входные данные: сумма должна быть больше нуля");
        }
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new InvalidCalculationInputException(
                        "Некорректные входные данные: сотрудник " + employeeId + " не найден"));
        BigDecimal base = BigDecimal.valueOf(amount);
        if (campaignId != null) {
            Campaign campaign = campaignRepository.findById(campaignId)
                    .orElseThrow(() -> new InvalidCalculationInputException(
                            "Некорректные входные данные: кампания " + campaignId + " не найдена"));
            boolean targeted = campaignDepartmentRepository
                    .existsByCampaignIdAndDepartmentId(campaignId, employee.getDepartment().getId());
            if (campaign.isRunningOn(onDate) && targeted) {
                int total = roundToStep(base.multiply(campaign.getCoefficient()), 1);
                if (fitsBudget(campaign, total)) {
                    return new CalculationResult(employeeId, null, base, campaign.getCoefficient(), campaign.getId(),
                            total, amount + " × " + campaign.getCoefficient().toPlainString()
                            + " (кампания «" + campaign.getName() + "») = " + total);
                }
            }
        }
        return new CalculationResult(employeeId, null, base, BigDecimal.ONE, null, amount, String.valueOf(amount));
    }

    /** Остаток бюджета кампании позволяет провести {@code amount}; кампания без бюджета не ограничена. */
    private boolean fitsBudget(Campaign campaign, int amount) {
        if (campaign.getBudget() == null) {
            return true;
        }
        long spent = operationRepository.sumAmountByCampaign(campaign.getId(), BUDGET_STATUSES);
        return spent + amount <= campaign.getBudget();
    }

    /** Шаг округления из JSONB params правила ({@code roundTo}). */
    private int roundTo(AccrualRule rule) {
        try {
            JsonNode node = objectMapper.readTree(rule.getParams()).path("roundTo");
            return node.canConvertToInt() && node.asInt() > 0 ? node.asInt() : DEFAULT_ROUND_TO;
        } catch (JsonProcessingException e) {
            throw new InvalidCalculationInputException(
                    "Некорректные параметры правила «" + rule.getName() + "»: params не является JSON");
        }
    }

    /** Округление до ближайшего кратного {@code step} (половина — вверх). */
    static int roundToStep(BigDecimal value, int step) {
        BigDecimal stepValue = BigDecimal.valueOf(step);
        return value.divide(stepValue, 0, RoundingMode.HALF_UP).multiply(stepValue).intValueExact();
    }
}
