package by.technosoft.bonus.service.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import by.technosoft.bonus.config.BonusProperties;
import by.technosoft.bonus.domain.AccountStatus;
import by.technosoft.bonus.domain.BonusAccount;
import by.technosoft.bonus.domain.DemoData;
import by.technosoft.bonus.domain.Operation;
import by.technosoft.bonus.domain.OperationBasis;
import by.technosoft.bonus.domain.OperationStatus;
import by.technosoft.bonus.domain.OperationType;
import by.technosoft.bonus.domain.TestEntities;
import by.technosoft.bonus.repository.BonusAccountRepository;
import by.technosoft.bonus.repository.OperationRepository;
import by.technosoft.bonus.repository.OperationReviewRepository;
import by.technosoft.bonus.service.exception.AccountBlockedException;
import by.technosoft.bonus.service.exception.DomainValidationException;
import by.technosoft.bonus.service.exception.ObjectNotFoundException;
import by.technosoft.bonus.service.query.AccountQueryService.AccountOverview;
import by.technosoft.bonus.service.query.AccountQueryService.MonthBalance;
import by.technosoft.bonus.service.query.OperationQueryService.OperationEntry;

/** UC1 «Просмотр баланса» и UC2 «Просмотр истории операций». */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccountQueryService — баланс и история операций (UC1, UC2)")
class AccountQueryServiceTest {

    @Mock private BonusAccountRepository accountRepository;
    @Mock private OperationRepository operationRepository;
    @Mock private OperationReviewRepository reviewRepository;

    private final DemoData d = new DemoData();
    /** Счёт Анны: баланс 2 500 Б = сумма проведённых операций ниже. */
    private final BonusAccount account = TestEntities.account(1, d.anna, 2500);
    /** Журнал счёта, новые сверху (так его возвращает репозиторий). */
    private List<Operation> journal;
    private AccountQueryService service;

    @BeforeEach
    void setUp() {
        BonusProperties properties = new BonusProperties(1000, 100, TestEntities.ZONE,
                new BonusProperties.EventScheduler(false, Duration.ofMinutes(1)));
        OperationQueryService operations = new OperationQueryService(operationRepository, reviewRepository, properties,
                TestEntities.clock());
        service = new AccountQueryService(accountRepository, operationRepository, operations, TestEntities.clock());
        journal = List.of(
                op(7, OperationType.ACCRUAL, OperationStatus.PENDING, 1500, "2026-11-23"),
                op(6, OperationType.WRITEOFF, OperationStatus.WRITTEN_OFF, -1500, "2026-11-20"),
                op(5, OperationType.ACCRUAL, OperationStatus.ACCRUED, 500, "2026-11-03"),
                op(4, OperationType.WRITEOFF, OperationStatus.WRITTEN_OFF, -900, "2026-10-12"),
                op(3, OperationType.ACCRUAL, OperationStatus.ACCRUED, 1000, "2026-09-05"),
                op(2, OperationType.ACCRUAL, OperationStatus.ACCRUED, 2000, "2026-08-01"),
                op(1, OperationType.ACCRUAL, OperationStatus.ACCRUED, 1400, "2026-06-10"));
        lenient().when(accountRepository.findByEmployeeId(1L)).thenReturn(Optional.of(account));
    }

    private Operation op(long id, OperationType type, OperationStatus status, int amount, String date) {
        return TestEntities.operation(id, account, type, status,
                type == OperationType.WRITEOFF ? OperationBasis.PROGRAM : OperationBasis.KPI, amount,
                TestEntities.at(date, 11));
    }

    @SuppressWarnings("unchecked")
    private void journalReturns(List<Operation> ops) {
        when(operationRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(ops);
    }

    // ── UC1 ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("FR1-1: авторизованному сотруднику отображается текущий бонусный баланс и сумма на согласовании")
    void showsCurrentBalance() {
        journalReturns(journal);

        AccountOverview overview = service.overview(1L);

        assertThat(overview.state().account().getBalance()).isEqualTo(2500);
        assertThat(overview.state().pending()).isEqualTo(1500);
        assertThat(overview.accruedThisMonth()).isEqualTo(500);
        assertThat(overview.spentThisMonth()).isEqualTo(1500);
        assertThat(overview.recent()).extracting(e -> e.operation().getId()).containsExactly(7L, 6L, 5L, 4L, 3L);
    }

    @Test
    @DisplayName("FR1-1: динамика баланса за 6 месяцев рассчитывается по журналу проведённых операций")
    void showsBalanceHistory() {
        journalReturns(journal);

        List<MonthBalance> history = service.overview(1L).history();

        assertThat(history).extracting(MonthBalance::month).containsExactly(
                YearMonth.of(2026, 6), YearMonth.of(2026, 7), YearMonth.of(2026, 8),
                YearMonth.of(2026, 9), YearMonth.of(2026, 10), YearMonth.of(2026, 11));
        assertThat(history).extracting(MonthBalance::balance).containsExactly(1400, 1400, 3400, 4400, 3500, 2500);
    }

    @Test
    @DisplayName("FR1-2: если счёт заблокирован (данные недоступны) — сообщение «Баланс недоступен, обратитесь к HR.»")
    void blockedAccountShowsError() {
        BonusAccount blocked = TestEntities.account(9, d.pavel, 0, AccountStatus.BLOCKED);
        when(accountRepository.findByEmployeeId(9L)).thenReturn(Optional.of(blocked));

        assertThatThrownBy(() -> service.overview(9L))
                .isInstanceOf(AccountBlockedException.class)
                .hasMessage("Баланс недоступен, обратитесь к HR.");
        verifyNoInteractions(operationRepository);
    }

    @Test
    @DisplayName("FR1-2: если к учётной записи не привязан счёт — сообщение об ошибке")
    void missingAccountShowsError() {
        when(accountRepository.findByEmployeeId(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.overview(404L))
                .isInstanceOf(ObjectNotFoundException.class)
                .hasMessage("К учётной записи не привязан бонусный счёт.");
        assertThatThrownBy(() -> service.overview(null)).isInstanceOf(ObjectNotFoundException.class);
    }

    @Test
    @DisplayName("FR1-3: после изменения баланса отображается актуальное значение")
    void showsUpdatedBalance() {
        journalReturns(journal);
        assertThat(service.overview(1L).state().account().getBalance()).isEqualTo(2500);

        account.applyDelta(700, TestEntities.now());

        AccountOverview updated = service.overview(1L);
        assertThat(updated.state().account().getBalance()).isEqualTo(3200);
        assertThat(updated.history().get(5).balance()).isEqualTo(3200);
        assertThat(service.state(1L).account().getBalance()).isEqualTo(3200);
    }

    // ── UC2 ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("FR2-1: авторизованному сотруднику отображается история операций его счёта с пояснениями")
    void showsHistory() {
        journalReturns(journal);
        when(reviewRepository.findByOperationIds(any())).thenReturn(List.of());

        List<OperationEntry> history = service.history(1L, null, null, null);

        assertThat(history).hasSize(7);
        assertThat(history.get(0).operation().getId()).isEqualTo(7L);
        assertThat(history.get(0).comment()).isEqualTo("Превышен лимит 1\u00A0000\u00A0Б на сотрудника");
        assertThat(history.get(1).comment()).isNull();
    }

    @Test
    @DisplayName("FR2-2: если операций нет — отображается пустой список")
    void emptyHistory() {
        journalReturns(List.of());

        assertThat(service.history(1L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), OperationType.ACCRUAL))
                .isEmpty();
        verify(reviewRepository, never()).findByOperationIds(any());
    }

    @Test
    @DisplayName("FR2-3: период фильтрации проверяется — начало позже окончания отклоняется")
    void periodIsValidated() {
        assertThatThrownBy(() -> service.history(1L, LocalDate.of(2026, 11, 30), LocalDate.of(2026, 11, 1), null))
                .isInstanceOf(DomainValidationException.class)
                .hasMessage("Дата начала периода позже даты окончания.");
        verify(operationRepository, never()).findAll(any(Specification.class), any(Sort.class));
    }

    @Test
    @DisplayName("FR2-3: период из одного дня и открытые границы периода допустимы")
    void singleDayAndOpenPeriods() {
        journalReturns(List.of(journal.get(2)));
        when(reviewRepository.findByOperationIds(any())).thenReturn(List.of());

        assertThat(service.history(1L, LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 3), null)).hasSize(1);
        assertThat(service.history(1L, LocalDate.of(2026, 11, 1), null, null)).hasSize(1);
        assertThat(service.history(1L, null, LocalDate.of(2026, 11, 30), null)).hasSize(1);
    }
}
