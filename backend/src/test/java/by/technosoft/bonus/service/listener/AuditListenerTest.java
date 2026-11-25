package by.technosoft.bonus.service.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import by.technosoft.bonus.domain.AuditLog;
import by.technosoft.bonus.domain.DemoData;
import by.technosoft.bonus.domain.OperationStatus;
import by.technosoft.bonus.domain.TestEntities;
import by.technosoft.bonus.repository.AppUserRepository;
import by.technosoft.bonus.repository.AuditLogRepository;
import by.technosoft.bonus.service.event.EventProcessingFailedEvent;
import by.technosoft.bonus.service.event.OperationAction;
import by.technosoft.bonus.service.event.OperationRegisteredEvent;

/** Журнал аудита операций (NFR-3, SEC-4): каждое значимое действие с операцией фиксируется. */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuditListener — журнал аудита операций")
class AuditListenerTest {

    @Mock private AuditLogRepository auditLogRepository;
    @Mock private AppUserRepository userRepository;

    private final ObjectMapper json = new ObjectMapper();
    private final DemoData d = new DemoData();
    private AuditListener listener;

    @BeforeEach
    void setUp() {
        listener = new AuditListener(auditLogRepository, userRepository, json, TestEntities.clock());
    }

    private AuditLog saved() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("FR7-3: корректировка фиксируется в журнале: автор, номер, исходная и новая сумма, статус")
    void correctionIsRecorded() throws Exception {
        when(userRepository.getReferenceById(20L)).thenReturn(d.hrUser);

        listener.onOperationRegistered(new OperationRegisteredEvent(OperationAction.CORRECTION, 320L, "ОП-000320", 1L,
                20L, 700, 1000, OperationStatus.CORRECTED, "Ошибка в сумме", null));

        AuditLog log = saved();
        assertThat(log.getAction()).isEqualTo("Корректировка");
        assertThat(log.getObjectType()).isEqualTo("operation");
        assertThat(log.getObjectId()).isEqualTo(320L);
        assertThat(log.getUser()).isSameAs(d.hrUser);
        assertThat(log.getOccurredAt()).isEqualTo(TestEntities.now());
        JsonNode details = json.readTree(log.getDetails());
        assertThat(details.get("number").asText()).isEqualTo("ОП-000320");
        assertThat(details.get("from").asInt()).isEqualTo(1000);
        assertThat(details.get("amount").asInt()).isEqualTo(700);
        assertThat(details.get("status").asText()).isEqualTo("corrected");
        assertThat(details.has("comment")).isFalse();
    }

    @Test
    @DisplayName("FR4-3: автоматическое начисление фиксируется в журнале от имени системы с формулой расчёта")
    void autoAccrualIsRecordedAsSystem() throws Exception {
        listener.onOperationRegistered(new OperationRegisteredEvent(OperationAction.AUTO_ACCRUAL, 330L, "ОП-000330",
                3L, null, 1700, null, OperationStatus.ACCRUED, "Выполнение плана спринта", "1140 × 1,5"));

        AuditLog log = saved();
        assertThat(log.getAction()).isEqualTo("Автоначисление по событию");
        assertThat(log.getUser()).isNull();
        assertThat(json.readTree(log.getDetails()).get("comment").asText()).isEqualTo("1140 × 1,5");
    }

    @Test
    @DisplayName("FR13-2: ошибка обработки внешнего события фиксируется в журнале аудита")
    void failedEventIsRecorded() throws Exception {
        listener.onEventProcessingFailed(new EventProcessingFailedEvent(176L, "kpi_closed", 5L, "значение KPI отсутствует"));

        AuditLog log = saved();
        assertThat(log.getAction()).isEqualTo("Ошибка автоначисления");
        assertThat(log.getObjectType()).isEqualTo("business_event");
        assertThat(log.getObjectId()).isEqualTo(176L);
        assertThat(json.readTree(log.getDetails()).get("error").asText()).isEqualTo("значение KPI отсутствует");
    }
}
