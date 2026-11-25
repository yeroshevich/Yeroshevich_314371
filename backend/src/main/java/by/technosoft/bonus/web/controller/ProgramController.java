package by.technosoft.bonus.web.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import by.technosoft.bonus.service.RedemptionService;
import by.technosoft.bonus.service.dto.RedemptionResult;
import by.technosoft.bonus.service.query.AccountQueryService;
import by.technosoft.bonus.service.query.DirectoryQueryService;
import by.technosoft.bonus.service.query.OperationQueryService;
import by.technosoft.bonus.web.dto.ProgramDto;
import by.technosoft.bonus.web.dto.RedemptionDto;
import by.technosoft.bonus.web.mapper.AccountMapper;
import by.technosoft.bonus.web.mapper.DirectoryMapper;
import by.technosoft.bonus.web.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Программы мотивации и списание баллов (UC3). */
@RestController
@RequestMapping(ApiPaths.V1 + "/programs")
@PreAuthorize(Roles.ACCOUNT_HOLDER)
@Tag(name = "Программы мотивации", description = "UC3 «Использование бонусов»")
public class ProgramController {

    private final DirectoryQueryService directoryQueryService;
    private final RedemptionService redemptionService;
    private final OperationQueryService operationQueryService;
    private final AccountQueryService accountQueryService;
    private final DirectoryMapper directoryMapper;
    private final AccountMapper accountMapper;

    public ProgramController(DirectoryQueryService directoryQueryService,
                             RedemptionService redemptionService,
                             OperationQueryService operationQueryService,
                             AccountQueryService accountQueryService,
                             DirectoryMapper directoryMapper,
                             AccountMapper accountMapper) {
        this.directoryQueryService = directoryQueryService;
        this.redemptionService = redemptionService;
        this.operationQueryService = operationQueryService;
        this.accountQueryService = accountQueryService;
        this.directoryMapper = directoryMapper;
        this.accountMapper = accountMapper;
    }

    @GetMapping
    @Operation(summary = "Каталог программ", description = "Активные и недоступные программы (с пояснением).")
    public List<ProgramDto> list() {
        return directoryQueryService.programs().stream().map(directoryMapper::toDto).toList();
    }

    @PostMapping("/{id}/redemptions")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Списать баллы на программу", description = "Баланс ≥ стоимости и программа активна. "
            + "409 INSUFFICIENT_FUNDS / PROGRAM_INACTIVE, 423 ACCOUNT_BLOCKED, 404 NOT_FOUND.")
    public RedemptionDto redeem(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id) {
        RedemptionResult result = redemptionService.redeem(me.employeeId(), id);
        return accountMapper.toDto(operationQueryService.get(result.operation().id()),
                accountQueryService.state(me.employeeId()));
    }
}
