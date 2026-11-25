package by.technosoft.bonus.web.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import by.technosoft.bonus.service.query.DirectoryQueryService;
import by.technosoft.bonus.service.query.EmployeeQueryService;
import by.technosoft.bonus.web.dto.DepartmentDto;
import by.technosoft.bonus.web.dto.EmployeeDto;
import by.technosoft.bonus.web.dto.TeamMemberDto;
import by.technosoft.bonus.web.mapper.DirectoryMapper;
import by.technosoft.bonus.web.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;

/** Подразделения и сотрудники с учётом области ответственности (SEC-3). */
@RestController
@RequestMapping(ApiPaths.V1)
@Tag(name = "Сотрудники и подразделения")
public class DirectoryController {

    private final DirectoryQueryService directoryQueryService;
    private final EmployeeQueryService employeeQueryService;
    private final DirectoryMapper mapper;

    public DirectoryController(DirectoryQueryService directoryQueryService, EmployeeQueryService employeeQueryService,
                               DirectoryMapper mapper) {
        this.directoryQueryService = directoryQueryService;
        this.employeeQueryService = employeeQueryService;
        this.mapper = mapper;
    }

    @GetMapping("/departments")
    @Operation(summary = "Подразделения", description = "Доступно любому вошедшему пользователю.")
    public List<DepartmentDto> departments() {
        return directoryQueryService.departments().stream().map(mapper::toDto).toList();
    }

    @GetMapping("/employees")
    @PreAuthorize(Roles.STAFF_VIEWERS)
    @Operation(summary = "Сотрудники", description = "Руководитель видит только подчинённых; HR, финотдел и "
            + "администратор — всех (scope=team — только своих подчинённых).")
    public List<EmployeeDto> employees(
            @AuthenticationPrincipal AuthenticatedUser me,
            @Parameter(description = "team | all (по умолчанию all)") @RequestParam(defaultValue = "all")
            @Pattern(regexp = "team|all", message = "scope: team или all.") String scope) {
        return employeeQueryService.visibleEmployees(me.userId(), "team".equals(scope)).stream()
                .map(mapper::toDto).toList();
    }

    @GetMapping("/employees/team")
    @PreAuthorize(Roles.MANAGER_OR_HR)
    @Operation(summary = "Команда с балансами и KPI", description = "Руководитель — подчинённые, HR — все сотрудники.")
    public List<TeamMemberDto> team(@AuthenticationPrincipal AuthenticatedUser me) {
        return employeeQueryService.team(me.userId()).stream().map(mapper::toDto).toList();
    }
}
