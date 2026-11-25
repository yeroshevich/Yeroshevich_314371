package by.technosoft.bonus.web.mapper;

import static by.technosoft.bonus.web.mapper.ApiValues.code;
import static by.technosoft.bonus.web.mapper.ApiValues.id;
import static by.technosoft.bonus.web.mapper.ApiValues.number;

import org.springframework.stereotype.Component;

import by.technosoft.bonus.domain.AccrualRule;
import by.technosoft.bonus.domain.RulePeriodicity;
import by.technosoft.bonus.service.admin.RuleAdminService.RuleCommand;
import by.technosoft.bonus.service.admin.RuleAdminService.RuleSaveOutcome;
import by.technosoft.bonus.service.query.RuleQueryService.RuleEntry;
import by.technosoft.bonus.web.dto.RuleDto;
import by.technosoft.bonus.web.dto.RuleRequest;
import by.technosoft.bonus.web.dto.RuleSaveResultDto;

/** Правило начисления ↔ JSON (UC8). */
@Component
public class RuleMapper {

    public RuleDto toDto(RuleEntry entry) {
        AccrualRule r = entry.rule();
        return new RuleDto(id(r.getId()), r.getName(), r.getKpi().getName(), number(r.getWeight()), r.getBasePoints(),
                formula(r), code(r.getPeriodicity()), number(r.getThreshold()), r.isActive(),
                entry.usedInCurrentPeriod(), r.getUpdatedAt());
    }

    public RuleSaveResultDto toDto(RuleSaveOutcome outcome) {
        return new RuleSaveResultDto(toDto(outcome.entry()), outcome.effectiveFrom(), outcome.deferred());
    }

    public RuleCommand toCommand(RuleRequest dto) {
        return new RuleCommand(dto.name(), dto.kpi(), dto.weight(), dto.basePoints(),
                ApiValues.parse(RulePeriodicity.class, dto.periodicity()), dto.threshold(), dto.active());
    }

    /** Формула BR-4 для людей: «3 000 × 0,4 × уровень достижения». */
    static String formula(AccrualRule r) {
        String base = String.format("%,d", r.getBasePoints()).replace(',', ' ');
        String weight = number(r.getWeight()).toPlainString().replace('.', ',');
        return base + " × " + weight + " × уровень достижения";
    }
}
