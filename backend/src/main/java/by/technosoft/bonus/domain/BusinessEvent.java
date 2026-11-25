package by.technosoft.bonus.domain;

import java.time.OffsetDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Бизнес-событие из внешней системы или таймера (таблица business_event, UC4, SI-4).
 * Пара (integration_id, external_id) уникальна — повторная доставка не создаёт дубль.
 */
@Entity
@Table(name = "business_event")
public class BusinessEvent {

    /** Максимальная длина error_message в схеме. */
    private static final int ERROR_MESSAGE_LENGTH = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Источник; {@code null} — внутреннее событие или таймер. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "integration_id")
    private Integration integration;

    @Column(name = "external_id", nullable = false, length = 100)
    private String externalId;

    /** Тип события: kpi_closed, anniversary, stage_accepted … */
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    /** Данные события в JSONB, например {@code {"kpi": "SPRINT", "value": 104.6}}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "status", nullable = false, length = 10)
    private EventStatus status;

    @Column(name = "error_message", length = ERROR_MESSAGE_LENGTH)
    private String errorMessage;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    protected BusinessEvent() {
    }

    /** Регистрирует полученное событие в статусе «new». */
    public static BusinessEvent received(Integration integration, String externalId, String eventType,
                                         Employee employee, String payload, OffsetDateTime receivedAt) {
        BusinessEvent event = new BusinessEvent();
        event.integration = integration;
        event.externalId = externalId;
        event.eventType = eventType;
        event.employee = employee;
        event.payload = payload == null ? "{}" : payload;
        event.status = EventStatus.NEW;
        event.receivedAt = receivedAt;
        return event;
    }

    /** Событие обработано (начисление проведено или не положено). */
    public void markProcessed(OffsetDateTime now) {
        this.status = EventStatus.PROCESSED;
        this.errorMessage = null;
        this.processedAt = now;
    }

    /** Ошибка обработки: событие отложено, причина сохраняется для разбора (альт. поток UC4). */
    public void markFailed(String message) {
        this.status = EventStatus.FAILED;
        this.errorMessage = message == null || message.length() <= ERROR_MESSAGE_LENGTH
                ? message
                : message.substring(0, ERROR_MESSAGE_LENGTH);
        this.processedAt = null;
    }

    public Long getId() {
        return id;
    }

    public Integration getIntegration() {
        return integration;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getEventType() {
        return eventType;
    }

    public Employee getEmployee() {
        return employee;
    }

    public String getPayload() {
        return payload;
    }

    public EventStatus getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public OffsetDateTime getReceivedAt() {
        return receivedAt;
    }

    public OffsetDateTime getProcessedAt() {
        return processedAt;
    }
}
