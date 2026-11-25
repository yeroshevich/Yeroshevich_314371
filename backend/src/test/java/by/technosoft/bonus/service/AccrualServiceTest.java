package by.technosoft.bonus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
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
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import by.technosoft.bonus.config.BonusProperties;
import by.technosoft.bonus.domain.AppUser;
import by.technosoft.bonus.domain.BonusAccount;
import by.technosoft.bonus.domain.Campaign;
import by.technosoft.bonus.domain.CampaignStatus;
import by.technosoft.bonus.domain.DemoData;
import by.technosoft.bonus.domain.Operation;
import by.technosoft.bonus.domain.OperationBasis;
import by.technosoft.bonus.domain.OperationStatus;
import by.technosoft.bonus.domain.OperationType;
import by.technosoft.bonus.domain.RoleCode;
import by.technosoft.bonus.domain.TestEntities;
import by.technosoft.bonus.domain.UserStatus;
import by.technosoft.bonus.repository.AccrualRuleRepository;
import by.technosoft.bonus.repository.AppUserRepository;
import by.technosoft.bonus.repository.BonusAccountRepository;
import by.technosoft.bonus.repository.CampaignDepartmentRepository;
import by.technosoft.bonus.repository.CampaignRepository;
import by.technosoft.bonus.repository.EmployeeRepository;
import by.technosoft.bonus.repository.OperationRepository;
import by.technosoft.bonus.service.dto.AccrualPreview;
import by.technosoft.bonus.service.dto.AccrualRequest;
import by.technosoft.bonus.service.dto.AccrualResult;
import by.technosoft.bonus.service.event.OperationAction;
import by.technosoft.bonus.service.event.OperationRegisteredEvent;
import by.technosoft.bonus.service.exception.AccessDeniedDomainException;
import by.technosoft.bonus.service.exception.AccountBlockedException;
import by.technosoft.bonus.service.exception.DomainValidationException;
import by.technosoft.bonus.service.exception.InvalidCalculationInputException;
import by.technosoft.bonus.service.strategy.AccrualStrategyRegistry;
import by.technosoft.bonus.service.strategy.FixedEventAccrualStrategy;
import by.technosoft.bonus.service.strategy.KpiAccrualStrategy;

/**
 * UC5 «Ручное начисление» (алгоритм А3): права, сохранение суммы, лимит, обновление баланса.
 * Используются настоящие AccessPolicy, BonusCalculationService и OperationNumberGenerator, репозитории — заглушки.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccrualService — ручное начисление (UC5)")
class AccrualServiceTest {

    @Mock private AppUserRepository userRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private BonusAccountRepository accountRepository;
    @Mock private OperationRepository operationRepository;
    @Mock private AccrualRuleRepository ruleRepository;
    @Mock private CampaignRepository campaignRepository;
    @Mock private CampaignDepartmentRepository campaignDepartmentRepository;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private ApplicationEventPublisher events;

    private final DemoData d = new DemoData();
    private AccrualService service;

    @BeforeEach
    void setUp() {
        AccessPolicy accessPolicy = new AccessPolicy(userRepository);
        BonusCalculationService calculation = new BonusCalculationService(employeeRepository, ruleRepository,
                campaignRepository, campaignDepartmentRepository, operationRepository,
                new AccrualStrategyRegistry(List.of(new KpiAccrualStrategy(), new FixedEventAccrualStrategy())),
                new ObjectMapper());
        BonusProperties properties = new BonusProperties(1000, 100, TestEntities.ZONE,
                new BonusProperties.EventScheduler(false, Duration.ofMinutes(1)));
        service = new AccrualService(accessPolicy, employeeRepository, accountRepository, operationRepository,
                ruleRepository, campaignRepository, calculation, new OperationNumberGenerator(jdbcTemplate), events,
                properties, TestEntities.clock());

        lenient().when(userRepository.findWithRoleById(any())).thenReturn(Optional.empty());
        for (AppUser u : List.of(d.employeeUser, d.managerUser, d.hrUser, d.financeUser, d.adminUser)) {
            lenient().when(userRepository.findWithRoleById(u.getId())).thenReturn(Optional.of(u));
        }
        lenient().when(employeeRepository.findById(1L)).thenReturn(Optional.of(d.anna));
        lenient().when(employeeRepository.findById(3L)).thenReturn(Optional.of(d.maxim));
        lenient().when(employeeRepository.findById(9L)).thenReturn(Optional.of(d.pavel));
        lenient().when(accountRepository.lockByEmployeeId(1L)).thenReturn(Optional.of(d.annaAccount));
        lenient().when(accountRepository.lockByEmployeeId(3L)).thenReturn(Optional.of(d.maximAccount));
        lenient().when(accountRepository.lockByEmployeeId(9L)).thenReturn(Optional.of(d.pavelAccount));
        lenient().when(jdbcTemplate.queryForObject(OperationNumberGenerator.NEXT_VALUE_SQL, Long.class))
                .thenReturn(300L, 301L, 302L);
    }

    private static AccrualRequest request(List<Long> employees, int amount) {
        return new AccrualRequest(employees, amount, OperationBasis.ACHIEVEMENT, "  Доклад на митапе  ", null, null);
    }

    private List<Operation> savedOperations(int count) {
        ArgumentCaptor<Operation> captor = ArgumentCaptor.forClass(Operation.class);
        verify(operationRepository, times(count)).save(captor.capture());
        return captor.getAllValues();
    }

    // ── FR5-1: права доступа ──────────────────────────────────────────────────────

    @Test
    @DisplayName("FR5-1: руководитель создаёт начисление сотруднику своей команды")
    void managerAccruesOwnTeam() {
        AccrualResult result = service.create(2L, request(List.of(1L), 500));

        assertThat(result.accruedCount()).isEqualTo(1);
        assertThat(result.operations()).hasSize(1);
        verify(operationRepository).save(any(Operation.class));
    }

    @Test
    @DisplayName("FR5-1: HR создаёт начисление сотруднику любого подразделения")
    void hrAccruesAnyEmployee() {
        BonusAccount nataliaAccount = TestEntities.account(8, d.natalia, 3550);
        when(employeeRepository.findById(8L)).thenReturn(Optional.of(d.natalia));
        when(accountRepository.lockByEmployeeId(8L)).thenReturn(Optional.of(nataliaAccount));

        AccrualResult result = service.create(20L, request(List.of(8L), 700));

        assertThat(result.operations()).singleElement().satisfies(op -> {
            assertThat(op.employeeId()).isEqualTo(8L);
            assertThat(op.createdBy()).isEqualTo(20L);
        });
    }

    @Test
    @DisplayName("FR5-1: без прав (роль «сотрудник», «финотдел») начисление не создаётся")
    void withoutRightsAccrualIsDenied() {
        assertThatThrownBy(() -> service.create(1L, request(List.of(3L), 500)))
                .isInstanceOf(AccessDeniedDomainException.class)
                .hasMessage("Для этого действия у вашей роли нет прав.");
        assertThatThrownBy(() -> service.create(23L, request(List.of(3L), 500)))
                .isInstanceOf(AccessDeniedDomainException.class);
        verify(operationRepository, never()).save(any());
        verify(accountRepository, never()).lockByEmployeeId(anyLong());
    }

    @Test
    @DisplayName("FR5-1: руководитель не может начислить сотруднику чужой команды")
    void managerCannotAccrueForeignEmployee() {
        assertThatThrownBy(() -> service.create(2L, request(List.of(1L, 9L), 500)))
                .isInstanceOf(AccessDeniedDomainException.class)
                .hasMessageContaining("только своей команды");
        verify(operationRepository, never()).save(any());
        assertThat(d.annaAccount.getBalance()).isEqualTo(8900);
    }

    @Test
    @DisplayName("FR5-1: заблокированная или неизвестная учётная запись инициатора — отказ")
    void blockedOrUnknownActorIsDenied() {
        AppUser blockedHr = TestEntities.user(21, d.olga, RoleCode.HR, UserStatus.BLOCKED);
        when(userRepository.findWithRoleById(21L)).thenReturn(Optional.of(blockedHr));

        assertThatThrownBy(() -> service.create(21L, request(List.of(1L), 500)))
                .isInstanceOf(AccessDeniedDomainException.class)
                .hasMessageContaining("заблокирована");
        assertThatThrownBy(() -> service.create(999L, request(List.of(1L), 500)))
                .isInstanceOf(AccessDeniedDomainException.class)
                .hasMessageContaining("Сессия закончилась");
        assertThatThrownBy(() -> service.create(null, request(List.of(1L), 500)))
                .isInstanceOf(AccessDeniedDomainException.class);
    }

    // ── FR5-2: сохранение указанной суммы ────────────────────────────────────────────

    @Test
    @DisplayName("FR5-2: указанная сумма сохраняется операцией начисления с основанием, причиной и автором")
    void savesAccrualWithAmount() {
        service.create(2L, request(List.of(1L), 500));

        Operation op = savedOperations(1).get(0);
        assertThat(op.getId()).isEqualTo(300L);
        assertThat(op.getNumber()).isEqualTo("ОП-000300");
        assertThat(op.getType()).isEqualTo(OperationType.ACCRUAL);
        assertThat(op.getStatus()).isEqualTo(OperationStatus.ACCRUED);
        assertThat(op.getBasis()).isEqualTo(OperationBasis.ACHIEVEMENT);
        assertThat(op.getAmount()).isEqualTo(500);
        assertThat(op.getReason()).isEqualTo("Доклад на митапе");
        assertThat(op.getCreatedBy()).isSameAs(d.managerUser);
        assertThat(op.getCreatedAt()).isEqualTo(TestEntities.now());
        assertThat(op.getAccount()).isSameAs(d.annaAccount);
    }

    @Test
    @DisplayName("FR5-2: сумма выше лимита 1 000 Б сохраняется на согласование финотдела (pending)")
    void aboveLimitIsSavedAsPending() {
        AccrualResult result = service.create(2L, request(List.of(3L), 1200));

        assertThat(result.pendingCount()).isEqualTo(1);
        assertThat(result.accruedCount()).isZero();
        assertThat(savedOperations(1).get(0).getStatus()).isEqualTo(OperationStatus.PENDING);
        ArgumentCaptor<OperationRegisteredEvent> event = ArgumentCaptor.forClass(OperationRegisteredEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().action()).isEqualTo(OperationAction.MANUAL_ACCRUAL);
        assertThat(event.getValue().comment()).isEqualTo("Превышен лимит 1\u00A0000\u00A0Б на сотрудника");
    }

    @Test
    @DisplayName("FR5-2: сумма ровно на лимите (1 000 Б) проводится сразу")
    void exactlyLimitIsAccrued() {
        service.create(2L, request(List.of(3L), 1000));
        assertThat(savedOperations(1).get(0).getStatus()).isEqualTo(OperationStatus.ACCRUED);
    }

    @Test
    @DisplayName("FR5-2: начисление нескольким сотрудникам — по операции на каждого, повторы в списке убираются")
    void multipleEmployeesAreDeduplicated() {
        AccrualResult result = service.create(2L, request(List.of(3L, 1L, 3L), 400));

        assertThat(result.operations()).extracting(o -> o.employeeId()).containsExactly(1L, 3L);
        assertThat(savedOperations(2)).extracting(Operation::getNumber).containsExactly("ОП-000300", "ОП-000301");
    }

    @Test
    @DisplayName("FR5-2: начисление с кампанией сохраняет сумму с коэффициентом и ссылку на кампанию и правило")
    void savesCampaignAndRuleLinks() {
        Campaign autumn = TestEntities.campaign(1, "Осенний релиз 2.0", "2026-09-01", "2026-12-15", "1.50", 60000,
                CampaignStatus.ACTIVE);
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(autumn));
        when(campaignRepository.getReferenceById(1L)).thenReturn(autumn);
        when(campaignDepartmentRepository.existsByCampaignIdAndDepartmentId(1L, 1L)).thenReturn(true);
        when(operationRepository.sumAmountByCampaign(eq(1L), any())).thenReturn(0L);
        when(ruleRepository.findById(1L)).thenReturn(Optional.of(TestEntities.sprintRule()));

        service.create(2L, new AccrualRequest(List.of(1L), 600, OperationBasis.KPI, "Релиз 2.0", 1L, 1L));

        Operation op = savedOperations(1).get(0);
        assertThat(op.getAmount()).isEqualTo(900);
        assertThat(op.getCampaign()).isSameAs(autumn);
        assertThat(op.getRule().getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("FR5-2: основание не из списка KPI/проект/достижение отклоняется")
    void wrongBasisIsRejected() {
        AccrualRequest program = new AccrualRequest(List.of(1L), 500, OperationBasis.PROGRAM, "Причина", null, null);
        assertThatThrownBy(() -> service.create(2L, program))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("выберите основание");
        verify(operationRepository, never()).save(any());
    }

    @Test
    @DisplayName("FR5-2: без причины или с нулевой суммой начисление не сохраняется")
    void blankReasonOrZeroAmountIsRejected() {
        AccrualRequest blank = new AccrualRequest(List.of(1L), 500, OperationBasis.KPI, "   ", null, null);
        assertThatThrownBy(() -> service.create(2L, blank)).isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> service.create(2L, request(List.of(1L), 0)))
                .isInstanceOf(InvalidCalculationInputException.class);
        verify(operationRepository, never()).save(any());
    }

    @Test
    @DisplayName("FR5-2: неизвестный сотрудник в списке получателей — ошибка проверки")
    void unknownEmployeeIsRejected() {
        when(employeeRepository.findById(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(20L, request(List.of(404L), 500)))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("неизвестный сотрудник");
    }

    // ── FR5-3: обновление баланса ───────────────────────────────────────────────────

    @Test
    @DisplayName("FR5-3: после проведённого начисления баланс сотрудника увеличивается на сумму")
    void balanceGrowsAfterAccrual() {
        service.create(2L, request(List.of(1L), 500));

        assertThat(d.annaAccount.getBalance()).isEqualTo(9400);
        assertThat(d.annaAccount.getUpdatedAt()).isEqualTo(TestEntities.now());
    }

    @Test
    @DisplayName("FR5-3: начисление на согласовании баланс не меняет")
    void pendingDoesNotChangeBalance() {
        service.create(2L, request(List.of(3L), 1500));
        assertThat(d.maximAccount.getBalance()).isEqualTo(9950);
    }

    @Test
    @DisplayName("FR5-3: заблокированный счёт любого получателя отменяет всё начисление — балансы не меняются")
    void blockedRecipientCancelsWholeAccrual() {
        assertThatThrownBy(() -> service.create(20L, request(List.of(1L, 9L), 500)))
                .isInstanceOf(AccountBlockedException.class)
                .hasMessageContaining("Павел Карпович");
        assertThat(d.annaAccount.getBalance()).isEqualTo(8900);
        verify(operationRepository, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    @DisplayName("FR6-3: предварительный расчёт возвращает итог по строкам и число согласований, ничего не сохраняя")
    void previewReturnsTotalsWithoutSaving() {
        AccrualPreview preview = service.preview(2L, request(List.of(3L, 1L), 800));

        assertThat(preview.lines()).extracting(l -> l.employeeId()).containsExactly(1L, 3L);
        assertThat(preview.total()).isEqualTo(1600);
        assertThat(preview.limit()).isEqualTo(1000);
        assertThat(preview.approvalCount()).isZero();
        assertThat(service.preview(2L, request(List.of(1L), 1001)).approvalCount()).isEqualTo(1);
        verify(operationRepository, never()).save(any());
        assertThat(d.annaAccount.getBalance()).isEqualTo(8900);
    }
}
