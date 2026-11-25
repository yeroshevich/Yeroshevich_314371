package by.technosoft.bonus.web.mapper;

import static by.technosoft.bonus.web.mapper.ApiValues.code;
import static by.technosoft.bonus.web.mapper.ApiValues.id;
import static by.technosoft.bonus.web.mapper.ApiValues.number;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

import org.springframework.stereotype.Component;

import by.technosoft.bonus.domain.Campaign;
import by.technosoft.bonus.service.admin.CampaignAdminService.CampaignCommand;
import by.technosoft.bonus.service.exception.DomainValidationException;
import by.technosoft.bonus.service.query.CampaignQueryService.CampaignEntry;
import by.technosoft.bonus.web.dto.CampaignDto;
import by.technosoft.bonus.web.dto.CampaignRequest;

/** Бонусная кампания ↔ JSON (UC9). */
@Component
public class CampaignMapper {

    private final Clock clock;

    public CampaignMapper(Clock clock) {
        this.clock = clock;
    }

    public CampaignDto toDto(CampaignEntry entry) {
        Campaign c = entry.campaign();
        return new CampaignDto(id(c.getId()), c.getName(), c.getDescription(), c.getStartDate(), c.getEndDate(),
                c.getTargets().stream().map(t -> id(t.getId().getDepartmentId())).sorted().toList(),
                number(c.getCoefficient()), c.getBudget(), entry.spent(), code(c.getStatus()));
    }

    public CampaignCommand toCommand(CampaignRequest dto) {
        return new CampaignCommand(dto.name(), dto.description(), businessDate(dto.startDate()),
                businessDate(dto.endDate()), dto.departmentIds(), dto.coefficient(), dto.budget());
    }

    /**
     * Дата кампании из запроса: «2026-12-01» или момент ISO 8601 («2026-11-30T21:00:00.000Z» —
     * так браузер передаёт начало суток), переведённый в часовой пояс компании.
     */
    LocalDate businessDate(String value) {
        String text = value.trim();
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException notDate) {
            try {
                return OffsetDateTime.parse(text).atZoneSameInstant(clock.getZone()).toLocalDate();
            } catch (DateTimeParseException notMoment) {
                throw new DomainValidationException("Дата «" + text + "» не распознана: нужен формат ГГГГ-ММ-ДД.");
            }
        }
    }
}
