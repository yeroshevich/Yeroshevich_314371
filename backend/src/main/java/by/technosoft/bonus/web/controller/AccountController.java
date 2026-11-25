package by.technosoft.bonus.web.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import by.technosoft.bonus.domain.OperationType;
import by.technosoft.bonus.service.query.AccountQueryService;
import by.technosoft.bonus.web.dto.AccountSummaryDto;
import by.technosoft.bonus.web.dto.OperationDto;
import by.technosoft.bonus.web.mapper.AccountMapper;
import by.technosoft.bonus.web.mapper.ApiValues;
import by.technosoft.bonus.web.mapper.OperationMapper;
import by.technosoft.bonus.web.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;

/** Бонусный счёт текущего сотрудника (UC1, UC2). */
@RestController
@RequestMapping(ApiPaths.V1 + "/accounts/me")
@PreAuthorize(Roles.ACCOUNT_HOLDER)
@Tag(name = "Бонусный счёт", description = "UC1 «Просмотр баланса», UC2 «История операций»")
public class AccountController {

    private final AccountQueryService accountQueryService;
    private final AccountMapper accountMapper;
    private final OperationMapper operationMapper;

    public AccountController(AccountQueryService accountQueryService, AccountMapper accountMapper,
                             OperationMapper operationMapper) {
        this.accountQueryService = accountQueryService;
        this.accountMapper = accountMapper;
        this.operationMapper = operationMapper;
    }

    @GetMapping
    @Operation(summary = "Баланс и сводка", description = "Баланс, итоги месяца, последние операции, динамика за 6 месяцев. "
            + "Заблокированный счёт — 423 ACCOUNT_BLOCKED.")
    public AccountSummaryDto summary(@AuthenticationPrincipal AuthenticatedUser me) {
        return accountMapper.toDto(accountQueryService.overview(me.employeeId()));
    }

    @GetMapping("/operations")
    @Operation(summary = "История операций своего счёта", description = "Период [from; to] включительно и тип операции.")
    public List<OperationDto> history(
            @AuthenticationPrincipal AuthenticatedUser me,
            @Parameter(description = "Дата начала, YYYY-MM-DD") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Дата окончания, YYYY-MM-DD") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "accrual | writeoff | correction") @RequestParam(required = false)
            @Pattern(regexp = "accrual|writeoff|correction", message = "Тип: accrual, writeoff или correction.") String type) {
        return operationMapper.toDtos(accountQueryService.history(me.employeeId(), from, to,
                ApiValues.parse(OperationType.class, type)));
    }
}
