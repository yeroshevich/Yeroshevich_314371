package by.technosoft.bonus.service.listener;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import by.technosoft.bonus.domain.AppUser;
import by.technosoft.bonus.domain.AuditLog;
import by.technosoft.bonus.repository.AppUserRepository;
import by.technosoft.bonus.repository.AuditLogRepository;
import by.technosoft.bonus.service.event.EventProcessingFailedEvent;
import by.technosoft.bonus.service.event.OperationRegisteredEvent;

/**
 * Наблюдатель (Observer): пишет журнал аудита (NFR-3, SEC-4).
 * Синхронный {@link EventListener} выполняется в транзакции операции:
 * операция и запись аудита фиксируются или откатываются вместе.
 */
@Component
public class AuditListener {

    private final AuditLogRepository auditLogRepository;
    private final AppUserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AuditListener(AuditLogRepository auditLogRepository, AppUserRepository userRepository,
                         ObjectMapper objectMapper, Clock clock) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @EventListener
    public void onOperationRegistered(OperationRegisteredEvent event) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("number", event.operationNumber());
        details.put("employeeId", event.employeeId());
        details.put("amount", event.amount());
        if (event.originalAmount() != null) {
            details.put("from", event.originalAmount());
        }
        details.put("status", event.status().dbValue());
        if (event.comment() != null) {
            details.put("comment", event.comment());
        }
        AppUser actor = event.actorUserId() == null ? null : userRepository.getReferenceById(event.actorUserId());
        auditLogRepository.save(new AuditLog(OffsetDateTime.now(clock), actor, event.action().auditLabel(),
                "operation", event.operationId(), toJson(details), null));
    }

    @EventListener
    public void onEventProcessingFailed(EventProcessingFailedEvent event) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("eventType", event.eventType());
        details.put("employeeId", event.employeeId());
        details.put("error", event.message());
        auditLogRepository.save(new AuditLog(OffsetDateTime.now(clock), null, "Ошибка автоначисления",
                "business_event", event.eventId(), toJson(details), null));
    }

    private String toJson(Map<String, Object> details) {
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось сериализовать детали аудита", e);
        }
    }
}
