package by.technosoft.bonus.service.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import by.technosoft.bonus.domain.DemoData;
import by.technosoft.bonus.domain.Notification;
import by.technosoft.bonus.domain.OperationStatus;
import by.technosoft.bonus.domain.TestEntities;
import by.technosoft.bonus.repository.AppUserRepository;
import by.technosoft.bonus.repository.NotificationRepository;
import by.technosoft.bonus.service.event.OperationAction;
import by.technosoft.bonus.service.event.OperationRegisteredEvent;

/** Уведомление сотрудника об операциях по его счёту (сервис уведомлений, актор UC). */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationListener — уведомления сотрудника")
class NotificationListenerTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private AppUserRepository userRepository;

    private final DemoData d = new DemoData();

    private NotificationListener listener() {
        return new NotificationListener(notificationRepository, userRepository, TestEntities.clock());
    }

    private static OperationRegisteredEvent event(OperationAction action, int amount, Integer from,
                                                  OperationStatus status, String comment) {
        return new OperationRegisteredEvent(action, 300L, "ОП-000300", 1L, 2L, amount, from, status, "Релиз 2.0",
                comment);
    }

    @Test
    @DisplayName("FR5-3: после начисления сотрудник получает уведомление «Начислено N Б»")
    void accrualNotification() {
        when(userRepository.findByEmployeeId(1L)).thenReturn(Optional.of(d.employeeUser));

        listener().onOperationRegistered(event(OperationAction.MANUAL_ACCRUAL, 1500, null, OperationStatus.ACCRUED, null));

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        assertThat(saved.getValue().getUser()).isSameAs(d.employeeUser);
        assertThat(saved.getValue().getTitle()).isEqualTo("Начислено 1\u00A0500\u00A0Б");
        assertThat(saved.getValue().getText()).isEqualTo("Релиз 2.0");
    }

    @Test
    @DisplayName("FR1-3: заголовки уведомлений отражают изменение баланса по каждому виду операции")
    void titlesForAllActions() {
        assertThat(NotificationListener.title(event(OperationAction.MANUAL_ACCRUAL, 1500, null,
                OperationStatus.PENDING, null))).isEqualTo("Начисление на согласовании");
        assertThat(NotificationListener.title(event(OperationAction.REDEMPTION, -900, null,
                OperationStatus.WRITTEN_OFF, null))).isEqualTo("Списано 900\u00A0Б");
        assertThat(NotificationListener.title(event(OperationAction.CORRECTION, 700, 1000,
                OperationStatus.CORRECTED, null))).isEqualTo("Начисление скорректировано");
        assertThat(NotificationListener.title(event(OperationAction.CANCELLATION, -1000, 1000,
                OperationStatus.PENDING, null))).isEqualTo("Корректировка на согласовании");
        assertThat(NotificationListener.title(event(OperationAction.APPROVAL, 1500, null,
                OperationStatus.ACCRUED, null))).isEqualTo("Начисление подтверждено");
        assertThat(NotificationListener.title(event(OperationAction.BLOCKING, 1500, null,
                OperationStatus.BLOCKED, "Дубль"))).isEqualTo("Начисление заблокировано");
    }

    @Test
    @DisplayName("FR7-3: уведомление о корректировке содержит исходную и новую сумму")
    void correctionText() {
        assertThat(NotificationListener.text(event(OperationAction.CORRECTION, 700, 1000, OperationStatus.CORRECTED,
                null))).isEqualTo("ОП-000300: 1\u00A0000\u00A0Б → 700\u00A0Б. Релиз 2.0");
        assertThat(NotificationListener.text(event(OperationAction.CANCELLATION, -1000, 1000,
                OperationStatus.CANCELLED, null))).isEqualTo("ОП-000300: 1\u00A0000\u00A0Б → 0\u00A0Б. Релиз 2.0");
        assertThat(NotificationListener.text(event(OperationAction.BLOCKING, 1500, null, OperationStatus.BLOCKED,
                "Дублирует начисление"))).isEqualTo("ОП-000300: Дублирует начисление");
    }

    @Test
    @DisplayName("FR5-3: сбой записи уведомления не прерывает операцию; без учётной записи уведомление не создаётся")
    void notificationFailureIsSwallowed() {
        when(userRepository.findByEmployeeId(1L)).thenReturn(Optional.of(d.employeeUser));
        when(notificationRepository.save(any())).thenThrow(new IllegalStateException("БД недоступна"));
        assertThatCode(() -> listener().onOperationRegistered(
                event(OperationAction.APPROVAL, 1500, null, OperationStatus.ACCRUED, null))).doesNotThrowAnyException();

        when(userRepository.findByEmployeeId(9L)).thenReturn(Optional.empty());
        listener().onOperationRegistered(new OperationRegisteredEvent(OperationAction.MANUAL_ACCRUAL, 1L, "ОП-000001",
                9L, 2L, 100, null, OperationStatus.ACCRUED, "x", null));
        verify(notificationRepository).save(any()); // только одна попытка — для сотрудника 1
    }
}
