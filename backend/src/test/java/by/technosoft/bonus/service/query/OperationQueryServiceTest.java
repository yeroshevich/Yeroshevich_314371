package by.technosoft.bonus.service.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import by.technosoft.bonus.config.BonusProperties;
import by.technosoft.bonus.domain.DemoData;
import by.technosoft.bonus.domain.Operation;
import by.technosoft.bonus.domain.OperationBasis;
import by.technosoft.bonus.domain.OperationReview;
import by.technosoft.bonus.domain.OperationStatus;
import by.technosoft.bonus.domain.OperationType;
import by.technosoft.bonus.domain.ReviewDecision;
import by.technosoft.bonus.domain.TestEntities;
import by.technosoft.bonus.repository.OperationRepository;
import by.technosoft.bonus.repository.OperationReviewRepository;
import by.technosoft.bonus.service.exception.DomainValidationException;
import by.technosoft.bonus.service.exception.ObjectNotFoundException;
import by.technosoft.bonus.service.query.OperationQueryService.OperationEntry;
import by.technosoft.bonus.service.query.OperationQueryService.OperationFilter;

/** Журнал операций (HR, финотдел): поиск, очередь контроля, пояснения к операциям. */
@ExtendWith(MockitoExtension.class)
@DisplayName("OperationQueryService — журнал операций и очередь контроля")
class OperationQueryServiceTest {

    @Mock private OperationRepository operationRepository;
    @Mock private OperationReviewRepository reviewRepository;

    private final DemoData d = new DemoData();
    private OperationQueryService service;
    private Operation annaAccrual;
    private Operation maximPending;
    private Operation maximCorrection;

    @BeforeEach
    void setUp() {
        BonusProperties properties = new BonusProperties(1000, 100, TestEntities.ZONE,
                new BonusProperties.EventScheduler(false, Duration.ofMinutes(1)));
        service = new OperationQueryService(operationRepository, reviewRepository, properties, TestEntities.clock());
        annaAccrual = TestEntities.operation(1, d.annaAccount, OperationType.ACCRUAL, OperationStatus.ACCRUED,
                OperationBasis.KPI, 1400, TestEntities.at("2026-06-25", 10));
        maximPending = TestEntities.operation(146, d.maximAccount, OperationType.ACCRUAL, OperationStatus.PENDING,
                OperationBasis.ACHIEVEMENT, 1500, TestEntities.at("2026-11-23", 10));
        maximCorrection = TestEntities.operation(150, d.maximAccount, OperationType.CORRECTION,
                OperationStatus.PENDING, OperationBasis.CORRECTION, -1900, TestEntities.at("2026-11-24", 10));
    }

    @SuppressWarnings("unchecked")
    private void repositoryReturns(List<Operation> ops) {
        when(operationRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(ops);
    }

    private static OperationFilter text(String q) {
        return new OperationFilter(null, null, null, null, null, null, q);
    }

    @Test
    @DisplayName("FR7-1: поиск в журнале по ФИО без учёта регистра (кириллица) — найти ошибочное начисление")
    void searchByNameIgnoresCase() {
        repositoryReturns(List.of(maximPending, annaAccrual));

        assertThat(service.search(text("новик"))).extracting(e -> e.operation().getId()).containsExactly(146L);
        assertThat(service.search(text("  КОВАЛЁВА "))).extracting(e -> e.operation().getId()).containsExactly(1L);
        assertThat(service.search(text("ОП-000001"))).hasSize(1);
        assertThat(service.search(text(""))).hasSize(2);
    }

    @Test
    @DisplayName("FR2-3: журнал не принимает период, у которого начало позже окончания")
    void searchValidatesPeriod() {
        OperationFilter wrong = new OperationFilter(null, null, null, Set.of(OperationStatus.ACCRUED),
                LocalDate.of(2026, 12, 1), LocalDate.of(2026, 11, 1), null);
        assertThatThrownBy(() -> service.search(wrong)).isInstanceOf(DomainValidationException.class);
    }

    @Test
    @DisplayName("FR5-2: операции на согласовании получают пояснение причины (лимит или отрицательный баланс)")
    void pendingOperationsAreExplained() {
        when(reviewRepository.findByOperationIds(any())).thenReturn(List.of());

        List<OperationEntry> entries = service.describe(List.of(maximPending, maximCorrection, annaAccrual));

        assertThat(entries).extracting(OperationEntry::comment).containsExactly(
                "Превышен лимит 1\u00A0000\u00A0Б на сотрудника",
                "Баланс сотрудника стал бы отрицательным: нужно согласование финотдела",
                null);
    }

    @Test
    @DisplayName("FR7-3: последнее решение финотдела отображается в журнале как комментарий; спорные помечаются")
    void reviewDecisionIsShown() {
        OperationReview disputed = new OperationReview(annaAccrual, d.financeUser, ReviewDecision.DISPUTED,
                "Проверить основание", TestEntities.now());
        when(reviewRepository.findByOperationIds(any())).thenReturn(List.of(disputed));

        OperationEntry entry = service.describe(List.of(annaAccrual)).get(0);

        assertThat(entry.comment()).isEqualTo("Проверить основание");
        assertThat(entry.disputed()).isTrue();
    }

    @Test
    @DisplayName("FR5-2: очередь контроля — операции на согласовании и проведённые спорные")
    @SuppressWarnings("unchecked")
    void controlQueueContainsPendingAndDisputed() {
        when(operationRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(maximPending), List.of(annaAccrual));
        when(reviewRepository.findOperationIdsByDecision(ReviewDecision.DISPUTED)).thenReturn(List.of(1L));
        when(reviewRepository.findByOperationIds(any())).thenReturn(List.of(new OperationReview(annaAccrual,
                d.financeUser, ReviewDecision.DISPUTED, "Спорно", TestEntities.now())));

        assertThat(service.controlQueue()).extracting(e -> e.operation().getId()).containsExactly(146L, 1L);
    }

    @Test
    @DisplayName("FR2-1: операция загружается по номеру; неизвестная — сообщение «не найдена»")
    void getById() {
        repositoryReturns(List.of(annaAccrual));
        assertThat(service.get(1L).operation()).isSameAs(annaAccrual);

        repositoryReturns(List.of());
        assertThatThrownBy(() -> service.get(404L)).isInstanceOf(ObjectNotFoundException.class);
        assertThat(service.getAll(List.of())).isEmpty();
    }
}
