package by.technosoft.bonus.web.mapper;

import static by.technosoft.bonus.web.mapper.ApiValues.code;
import static by.technosoft.bonus.web.mapper.ApiValues.id;

import java.util.Map;

import org.springframework.stereotype.Component;

import by.technosoft.bonus.domain.AppUser;
import by.technosoft.bonus.domain.AuditLog;
import by.technosoft.bonus.service.query.AuditQueryService.AuditRecord;
import by.technosoft.bonus.web.dto.AuditEntryDto;

/** Запись журнала аудита → JSON: тип объекта и автор — для людей. */
@Component
public class AuditMapper {

    private static final Map<String, String> OBJECT_TYPES = Map.of(
            "operation", "Операция",
            "accrual_rule", "Правило",
            "campaign", "Кампания",
            "app_user", "Пользователь",
            "integration", "Интеграция",
            "session", "Сессия",
            "business_event", "Событие",
            "program", "Программа");

    public AuditEntryDto toDto(AuditRecord record) {
        AuditLog log = record.log();
        AppUser user = log.getUser();
        return new AuditEntryDto(
                id(log.getId()),
                log.getOccurredAt(),
                user == null ? OperationMapper.SYSTEM_AUTHOR : user.getEmployee().getFullName(),
                user == null ? "system" : code(user.getRoleCode()),
                log.getAction(),
                OBJECT_TYPES.getOrDefault(log.getObjectType(), log.getObjectType()),
                record.objectLabel(),
                log.getIp() == null ? "—" : log.getIp().getHostAddress());
    }
}
