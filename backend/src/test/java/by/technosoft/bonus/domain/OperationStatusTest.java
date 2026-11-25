package by.technosoft.bonus.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Жизненный цикл операции (диаграмма состояний ЛР8) и инварианты счёта:
 * переходы статусов, учёт суммы в балансе, запрет отрицательного баланса.
 */
@DisplayName("OperationStatus, Operation, BonusAccount — жизненный цикл и инварианты")
class OperationStatusTest {

    @ParameterizedTest(name = "{0} → {1}: {2}")
    @CsvSource({
            "PENDING, ACCRUED, true", "PENDING, CORRECTED, true", "PENDING, CANCELLED, true", "PENDING, BLOCKED, true",
            "PENDING, WRITTEN_OFF, false", "PENDING, PENDING, false",
            "ACCRUED, CANCELLED, true", "ACCRUED, CORRECTED, false", "ACCRUED, PENDING, false",
            "CORRECTED, CANCELLED, true", "CORRECTED, ACCRUED, false",
            "WRITTEN_OFF, CANCELLED, false", "BLOCKED, ACCRUED, false", "CANCELLED, ACCRUED, false"})
    @DisplayName("FR7-2: переходы статуса операции соответствуют диаграмме состояний")
    void transitions(OperationStatus from, OperationStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }

    @Test
    @DisplayName("FR5-3: в баланс входят только начисленные, списанные и скорректированные операции")
    void settledStatuses() {
        assertThat(OperationStatus.SETTLED).containsExactlyInAnyOrder(
                OperationStatus.ACCRUED, OperationStatus.WRITTEN_OFF, OperationStatus.CORRECTED);
        assertThat(OperationStatus.PENDING.isSettled()).isFalse();
        assertThat(OperationStatus.BLOCKED.isSettled()).isFalse();
        assertThat(OperationStatus.CANCELLED.isSettled()).isFalse();
    }

    @Test
    @DisplayName("FR7-2: недопустимый переход операции отклоняется, статус не меняется")
    void illegalTransitionIsRejected() {
        DemoData d = new DemoData();
        Operation writeoff = TestEntities.operation(5, d.annaAccount, OperationType.WRITEOFF,
                OperationStatus.WRITTEN_OFF, OperationBasis.PROGRAM, -900, TestEntities.now());

        assertThatThrownBy(() -> writeoff.transitionTo(OperationStatus.CANCELLED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ОП-000005");
        assertThat(writeoff.getStatus()).isEqualTo(OperationStatus.WRITTEN_OFF);
    }

    @Test
    @DisplayName("FR7-2: разница корректировки — новая сумма минус исходная; для отмены — минус исходная")
    void correctionDelta() {
        DemoData d = new DemoData();
        Operation original = TestEntities.operation(14, d.annaAccount, OperationType.ACCRUAL, OperationStatus.ACCRUED,
                OperationBasis.KPI, 1000, TestEntities.now());
        Operation adjust = TestEntities.operation(20, d.annaAccount, OperationType.CORRECTION, OperationStatus.PENDING,
                OperationBasis.CORRECTION, 700, TestEntities.now()).correcting(original);
        Operation cancel = TestEntities.operation(21, d.annaAccount, OperationType.CORRECTION, OperationStatus.PENDING,
                OperationBasis.CORRECTION, -1000, TestEntities.now()).correcting(original);

        assertThat(adjust.correctionDelta()).isEqualTo(-300);
        assertThat(adjust.isCancellationRequest()).isFalse();
        assertThat(cancel.correctionDelta()).isEqualTo(-1000);
        assertThat(cancel.isCancellationRequest()).isTrue();
        assertThatThrownBy(original::correctionDelta).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("FR3-2: баланс счёта не может стать отрицательным (CHECK balance >= 0)")
    void balanceNeverNegative() {
        DemoData d = new DemoData();
        BonusAccount account = TestEntities.account(1, d.anna, 500);

        assertThatThrownBy(() -> account.applyDelta(-501, TestEntities.now()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(account.getBalance()).isEqualTo(500);
        account.applyDelta(-500, TestEntities.now());
        assertThat(account.getBalance()).isZero();
    }

    @Test
    @DisplayName("FR5-2: операция с нулевой суммой или причиной длиннее 300 символов не создаётся")
    void operationInvariants() {
        DemoData d = new DemoData();
        OperationNumber number = new OperationNumber(1L, "ОП-000001");

        assertThatThrownBy(() -> Operation.create(number, d.annaAccount, OperationType.ACCRUAL,
                OperationStatus.ACCRUED, OperationBasis.KPI, 0, "x", null, TestEntities.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Operation.create(number, d.annaAccount, OperationType.ACCRUAL,
                OperationStatus.ACCRUED, OperationBasis.KPI, 100, "x".repeat(301), null, TestEntities.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(Operation.create(number, d.annaAccount, OperationType.ACCRUAL, OperationStatus.ACCRUED,
                OperationBasis.KPI, 100, "x".repeat(300), null, TestEntities.now()).isNew()).isTrue();
    }

    @Test
    @DisplayName("FR8-3: правило действует, только если активно и дата вступления в силу наступила")
    void ruleApplicability() {
        AccrualRule rule = TestEntities.sprintRule();
        assertThat(rule.isApplicableOn(LocalDate.of(2026, 6, 1))).isTrue();
        assertThat(rule.isApplicableOn(LocalDate.of(2026, 5, 31))).isFalse();
        TestEntities.set(rule, "active", false);
        assertThat(rule.isApplicableOn(LocalDate.of(2026, 11, 25))).isFalse();
    }

    @Test
    @DisplayName("FR4-3: ошибка обработки события сохраняется не длиннее 255 символов; успешная обработка её очищает")
    void businessEventStatus() {
        DemoData d = new DemoData();
        BusinessEvent event = TestEntities.event(1, null, "kpi_closed", d.anna, null, TestEntities.now());

        assertThat(event.getPayload()).isEqualTo("{}");
        assertThat(event.getStatus()).isEqualTo(EventStatus.NEW);
        event.markFailed("x".repeat(300));
        assertThat(event.getErrorMessage()).hasSize(255);
        event.markProcessed(TestEntities.now());
        assertThat(event.getStatus()).isEqualTo(EventStatus.PROCESSED);
        assertThat(event.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("FR12-2: код роли из БД преобразуется в перечисление; неизвестный код — ошибка")
    void persistentEnum() {
        assertThat(PersistentEnum.fromDb(RoleCode.class, "finance")).isEqualTo(RoleCode.FINANCE);
        assertThatThrownBy(() -> PersistentEnum.fromDb(RoleCode.class, "root"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
