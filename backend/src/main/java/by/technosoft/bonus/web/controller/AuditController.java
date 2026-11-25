package by.technosoft.bonus.web.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import by.technosoft.bonus.service.query.AuditQueryService;
import by.technosoft.bonus.web.dto.AuditEntryDto;
import by.technosoft.bonus.web.mapper.AuditMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Журнал аудита (NFR-3, SEC-4). */
@RestController
@RequestMapping(ApiPaths.V1 + "/audit")
@PreAuthorize(Roles.ADMIN)
@Tag(name = "Аудит", description = "NFR-3, SEC-4: журнал действий пользователей и системы")
public class AuditController {

    private final AuditQueryService auditQueryService;
    private final AuditMapper mapper;

    public AuditController(AuditQueryService auditQueryService, AuditMapper mapper) {
        this.auditQueryService = auditQueryService;
        this.mapper = mapper;
    }

    @GetMapping
    @Operation(summary = "Записи журнала", description = "Новые сверху, не больше 1000 записей.")
    public List<AuditEntryDto> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Поиск по пользователю, действию и объекту") @RequestParam(required = false) String q) {
        return auditQueryService.search(from, to, q).stream().map(mapper::toDto).toList();
    }
}
