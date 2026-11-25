package by.technosoft.bonus.web.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import by.technosoft.bonus.service.query.ReportQueryService;
import by.technosoft.bonus.web.dto.ReportDto;
import by.technosoft.bonus.web.mapper.ReportMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;

/** Отчёты по начислениям и списаниям (UC10). */
@RestController
@RequestMapping(ApiPaths.V1 + "/reports")
@PreAuthorize(Roles.HR_OR_FINANCE)
@Tag(name = "Отчёты", description = "UC10 «Формирование отчётов»; экспорт в Excel/PDF выполняет клиент")
public class ReportController {

    private final ReportQueryService reportQueryService;
    private final ReportMapper mapper;

    public ReportController(ReportQueryService reportQueryService, ReportMapper mapper) {
        this.reportQueryService = reportQueryService;
        this.mapper = mapper;
    }

    @GetMapping("/{type}")
    @Operation(summary = "Сформировать отчёт", description = "Строки только с операциями за период [from; to].")
    public ReportDto build(
            @Parameter(description = "employees | departments | campaigns") @PathVariable
            @Pattern(regexp = "employees|departments|campaigns", message = "Вид отчёта: employees, departments или campaigns.")
            String type,
            @Parameter(description = "YYYY-MM-DD") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "YYYY-MM-DD") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return mapper.toDto(reportQueryService.build(ReportMapper.parse(type), from, to));
    }
}
