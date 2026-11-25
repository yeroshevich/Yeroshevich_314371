package by.technosoft.bonus.web.mapper;

import static by.technosoft.bonus.web.mapper.ApiValues.id;

import java.util.Locale;

import org.springframework.stereotype.Component;

import by.technosoft.bonus.service.query.ReportQueryService.Report;
import by.technosoft.bonus.service.query.ReportType;
import by.technosoft.bonus.web.dto.ReportDto;
import by.technosoft.bonus.web.dto.ReportRowDto;

/** Отчёт (UC10) → JSON. */
@Component
public class ReportMapper {

    public ReportDto toDto(Report report) {
        return new ReportDto(code(report.type()), report.from(), report.to(),
                report.rows().stream()
                        .map(r -> new ReportRowDto(id(r.key()), r.name(), r.group(), r.accrued(), r.spent(),
                                r.pending(), r.balance(), r.operations()))
                        .toList(),
                report.generatedAt());
    }

    /** employees | departments | campaigns. */
    public static String code(ReportType type) {
        return type.name().toLowerCase(Locale.ROOT);
    }

    public static ReportType parse(String code) {
        return ReportType.valueOf(code.toUpperCase(Locale.ROOT));
    }
}
