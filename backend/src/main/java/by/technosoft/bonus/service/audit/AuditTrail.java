package by.technosoft.bonus.service.audit;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import by.technosoft.bonus.domain.AppUser;
import by.technosoft.bonus.domain.AuditLog;
import by.technosoft.bonus.repository.AppUserRepository;
import by.technosoft.bonus.repository.AuditLogRepository;

/**
 * Запись административных действий в журнал аудита (NFR-3, SEC-4): правила, кампании,
 * пользователи, интеграции, вход и выход. Пишет в транзакции вызывающего сервиса —
 * действие и запись аудита фиксируются или откатываются вместе.
 * (Операции по счетам журналирует {@code AuditListener} по доменным событиям.)
 */
@Component
public class AuditTrail {

    private final AuditLogRepository auditLogRepository;
    private final AppUserRepository userRepository;
    private final ClientAddressProvider clientAddress;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AuditTrail(AuditLogRepository auditLogRepository, AppUserRepository userRepository,
                      ClientAddressProvider clientAddress, ObjectMapper objectMapper, Clock clock) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.clientAddress = clientAddress;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * @param actorUserId инициатор ({@code null} — система)
     * @param action      действие для людей: «Создание правила»
     * @param objectType  тип объекта (имя таблицы): accrual_rule, campaign, app_user, integration, session
     * @param objectId    идентификатор объекта
     * @param details     подробности; ключ {@code label} показывается в журнале как «объект»
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long actorUserId, String action, String objectType, Long objectId, Map<String, ?> details) {
        AppUser actor = actorUserId == null ? null : userRepository.getReferenceById(actorUserId);
        auditLogRepository.save(new AuditLog(OffsetDateTime.now(clock), actor, action, objectType, objectId,
                toJson(details), clientAddress.currentAddress().orElse(null)));
    }

    private String toJson(Map<String, ?> details) {
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось сериализовать детали аудита", e);
        }
    }
}
