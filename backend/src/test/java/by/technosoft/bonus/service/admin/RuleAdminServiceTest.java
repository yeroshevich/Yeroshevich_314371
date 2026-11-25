package by.technosoft.bonus.service.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import by.technosoft.bonus.domain.AccrualRule;
import by.technosoft.bonus.domain.AppUser;
import by.technosoft.bonus.domain.DemoData;
import by.technosoft.bonus.domain.Kpi;
import by.technosoft.bonus.domain.RulePeriodicity;
import by.technosoft.bonus.domain.TestEntities;
import by.technosoft.bonus.repository.AccrualRuleRepository;
import by.technosoft.bonus.repository.AppUserRepository;
import by.technosoft.bonus.repository.KpiRepository;
import by.technosoft.bonus.repository.OperationRepository;
import by.technosoft.bonus.service.AccessPolicy;
import by.technosoft.bonus.service.admin.RuleAdminService.RuleCommand;
import by.technosoft.bonus.service.admin.RuleAdminService.RuleSaveOutcome;
import by.technosoft.bonus.service.audit.AuditTrail;
import by.technosoft.bonus.service.exception.AccessDeniedDomainException;
import by.technosoft.bonus.service.exception.ConflictException;
import by.technosoft.bonus.service.exception.DomainValidationException;
import by.technosoft.bonus.service.exception.ObjectNotFoundException;
import by.technosoft.bonus.service.query.BonusPeriods;
import by.technosoft.bonus.service.query.RuleQueryService;

/** UC8 «Настройка правил»: создание HR, сохранение, применение изменений со следующего периода. */
@ExtendWith(MockitoExtension.class)
@DisplayName("RuleAdminService — настройка правил начисления (UC8)")
class RuleAdminServiceTest {

    private static final long HR = 20L;

    @Mock private AppUserRepository userRepository;
    @Mock private AccrualRuleRepository ruleRepository;
    @Mock private KpiRepository kpiRepository;
    @Mock private OperationRepository operationRepository;
    @Mock private AuditTrail auditTrail;

    private final DemoData d = new DemoData();
    private final AccrualRule sprint = TestEntities.sprintRule();
    private final Kpi csat = TestEntities.kpi(7, "KPI-7", "Индекс удовлетворённости клиентов");
    private RuleAdminService service;

    @BeforeEach
    void setUp() {
        RuleQueryService ruleQueryService = new RuleQueryService(ruleRepository, operationRepository,
                TestEntities.clock());
        service = new RuleAdminService(new AccessPolicy(userRepository), ruleRepository, kpiRepository,
                ruleQueryService, auditTrail, TestEntities.clock());
        for (AppUser u : new AppUser[] {d.managerUser, d.hrUser}) {
            lenient().when(userRepository.findWithRoleById(u.getId())).thenReturn(Optional.of(u));
        }
        lenient().when(kpiRepository.findAll()).thenReturn(List.of(sprint.getKpi()));
        lenient().when(ruleRepository.findWithKpiById(1L)).thenReturn(Optional.of(sprint));
        lenient().when(ruleRepository.findVersionsByKpiId(1L)).thenReturn(List.of(sprint));
    }

    private static RuleCommand csatRule() {
        return new RuleCommand("  Отзывы клиентов ", "Индекс удовлетворённости клиентов", new BigDecimal("0.25"), 4000,
                RulePeriodicity.QUARTER, new BigDecimal("85"), true);
    }

    private static RuleCommand sprintChange(String kpi) {
        return new RuleCommand("Выполнение плана спринта", kpi, new BigDecimal("0.50"), 3000, RulePeriodicity.MONTH,
                new BigDecimal("80"), true);
    }

    private void newKpiCreated() {
        when(kpiRepository.nextId()).thenReturn(7L);
        when(kpiRepository.findById(7L)).thenReturn(Optional.of(csat));
        when(ruleRepository.findVersionsByKpiId(7L)).thenReturn(List.of());
        when(ruleRepository.nextId()).thenReturn(7L);
        AccrualRule saved = TestEntities.rule(7, "Отзывы клиентов", csat, "0.25", 4000, RulePeriodicity.QUARTER, "85",
                "{}", true, TestEntities.TODAY);
        when(ruleRepository.findWithKpiById(7L)).thenReturn(Optional.of(saved));
    }

    // ── FR8-1 ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("FR8-1: авторизованный HR может создать правило начисления")
    void hrCanCreateRule() {
        newKpiCreated();

        RuleSaveOutcome outcome = service.create(HR, csatRule());

        assertThat(outcome.entry().rule().getName()).isEqualTo("Отзывы клиентов");
        assertThat(outcome.deferred()).isFalse();
    }

    @Test
    @DisplayName("FR8-1: руководитель и неавторизованный пользователь создавать и менять правила не могут")
    void nonHrCannotManageRules() {
        assertThatThrownBy(() -> service.create(2L, csatRule())).isInstanceOf(AccessDeniedDomainException.class);
        assertThatThrownBy(() -> service.update(2L, 1L, sprintChange("Закрытые задачи спринта")))
                .isInstanceOf(AccessDeniedDomainException.class);
        assertThatThrownBy(() -> service.create(null, csatRule())).isInstanceOf(AccessDeniedDomainException.class);
        verify(ruleRepository, never()).insert(anyLong(), anyString(), anyLong(), any(), anyInt(), anyString(), any(),
                anyBoolean(), any(), any());
    }

    // ── FR8-2 ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("FR8-2: после создания правило сохраняется с параметрами и вступает в силу сегодня")
    void createdRuleIsSaved() {
        newKpiCreated();

        RuleSaveOutcome outcome = service.create(HR, csatRule());

        verify(kpiRepository).insert(7L, "KPI-7", "Индекс удовлетворённости клиентов");
        verify(ruleRepository).insert(eq(7L), eq("Отзывы клиентов"), eq(7L), eq(new BigDecimal("0.25")), eq(4000),
                eq("quarter"), eq(new BigDecimal("85")), eq(true), eq(TestEntities.TODAY), eq(TestEntities.now()));
        assertThat(outcome.effectiveFrom()).isEqualTo(TestEntities.TODAY);
    }

    @Test
    @DisplayName("FR8-2: создание правила фиксируется в журнале аудита")
    @SuppressWarnings("unchecked")
    void creationIsAudited() {
        newKpiCreated();

        service.create(HR, csatRule());

        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);
        verify(auditTrail).record(eq(HR), eq("Создание правила"), eq("accrual_rule"), eq(7L), details.capture());
        assertThat(details.getValue()).containsEntry("label", "Отзывы клиентов").containsEntry("deferred", false);
    }

    @Test
    @DisplayName("FR8-2: для показателя, у которого уже есть правило, второе не создаётся (RULE_EXISTS)")
    void duplicateRuleForKpiIsRejected() {
        RuleCommand duplicate = new RuleCommand("Спринт-2", "закрытые задачи СПРИНТА", BigDecimal.ONE, 100,
                RulePeriodicity.MONTH, BigDecimal.TEN, true);

        assertThatThrownBy(() -> service.create(HR, duplicate))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.getCode()).isEqualTo(RuleAdminService.RULE_EXISTS))
                .hasMessageContaining("уже есть правило «Выполнение плана спринта»");
        verify(kpiRepository, never()).insert(anyLong(), anyString(), anyString());
    }

    // ── FR8-3 ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("FR8-3: правило, применённое в текущем периоде, меняется новой версией со следующего периода")
    void usedRuleChangesFromNextPeriod() {
        when(operationRepository.existsByRuleKpiSince(eq(1L), any())).thenReturn(true);
        when(ruleRepository.nextId()).thenReturn(8L);
        AccrualRule version = TestEntities.rule(8, "Выполнение плана спринта", sprint.getKpi(), "0.50", 3000,
                RulePeriodicity.MONTH, "80", "{\"roundTo\": 50}", true, LocalDate.of(2026, 12, 1));
        when(ruleRepository.findWithKpiById(8L)).thenReturn(Optional.of(version));

        RuleSaveOutcome outcome = service.update(HR, 1L, sprintChange("Закрытые задачи спринта"));

        assertThat(outcome.deferred()).isTrue();
        assertThat(outcome.effectiveFrom()).isEqualTo(LocalDate.of(2026, 12, 1));
        verify(ruleRepository).insertVersion(eq(8L), eq(1L), eq("Выполнение плана спринта"),
                eq(new BigDecimal("0.50")), eq(3000), eq("month"), eq(new BigDecimal("80")), eq(true),
                eq(LocalDate.of(2026, 12, 1)), any());
        verify(ruleRepository, never()).update(anyLong(), anyString(), anyLong(), any(), anyInt(), anyString(), any(),
                anyBoolean(), any());
        // Текущая версия продолжает действовать до конца ноября, новая — с 1 декабря
        assertThat(sprint.isApplicableOn(TestEntities.TODAY)).isTrue();
        assertThat(version.isApplicableOn(TestEntities.TODAY)).isFalse();
        assertThat(version.isApplicableOn(LocalDate.of(2026, 12, 1))).isTrue();
    }

    @Test
    @DisplayName("FR8-3: правило, не применявшееся в текущем периоде, меняется сразу")
    void unusedRuleChangesImmediately() {
        when(operationRepository.existsByRuleKpiSince(eq(1L), any())).thenReturn(false);

        RuleSaveOutcome outcome = service.update(HR, 1L, sprintChange("Закрытые задачи спринта"));

        assertThat(outcome.deferred()).isFalse();
        assertThat(outcome.effectiveFrom()).isEqualTo(TestEntities.TODAY);
        verify(ruleRepository).update(eq(1L), eq("Выполнение плана спринта"), eq(1L), eq(new BigDecimal("0.50")),
                eq(3000), eq("month"), eq(new BigDecimal("80")), eq(true), any());
    }

    @Test
    @DisplayName("FR8-3: у применённого правила нельзя сменить показатель; неизвестное правило — «не найдено»")
    void kpiOfUsedRuleIsLocked() {
        when(operationRepository.existsByRuleKpiSince(eq(1L), any())).thenReturn(true);
        assertThatThrownBy(() -> service.update(HR, 1L, sprintChange("Другой показатель")))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("менять нельзя");

        when(ruleRepository.findWithKpiById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(HR, 99L, sprintChange("x"))).isInstanceOf(ObjectNotFoundException.class);
    }

    @Test
    @DisplayName("FR8-3: следующий период правила — начало следующего месяца, квартала или года")
    void nextPeriodStart() {
        LocalDate today = TestEntities.TODAY;
        assertThat(BonusPeriods.nextPeriodStart(RulePeriodicity.MONTH, today)).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(BonusPeriods.nextPeriodStart(RulePeriodicity.EVENT, today)).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(BonusPeriods.nextPeriodStart(RulePeriodicity.QUARTER, today)).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(BonusPeriods.nextPeriodStart(RulePeriodicity.YEAR, today)).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(BonusPeriods.periodStart(RulePeriodicity.QUARTER, today)).isEqualTo(LocalDate.of(2026, 10, 1));
    }
}
