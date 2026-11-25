package by.technosoft.bonus.web.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import by.technosoft.bonus.service.AccrualService;
import by.technosoft.bonus.service.dto.AccrualResult;
import by.technosoft.bonus.service.dto.OperationView;
import by.technosoft.bonus.service.query.OperationQueryService;
import by.technosoft.bonus.web.dto.AccrualPreviewDto;
import by.technosoft.bonus.web.dto.AccrualPreviewRequestDto;
import by.technosoft.bonus.web.dto.AccrualRequestDto;
import by.technosoft.bonus.web.dto.AccrualResultDto;
import by.technosoft.bonus.web.mapper.AccrualMapper;
import by.technosoft.bonus.web.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Ручное начисление баллов (UC5) с расчётом (UC6). */
@RestController
@RequestMapping(ApiPaths.V1 + "/accruals")
@PreAuthorize(Roles.MANAGER_OR_HR)
@Tag(name = "Начисления", description = "UC5 «Ручное начисление», UC6 «Расчёт»")
public class AccrualController {

    private final AccrualService accrualService;
    private final OperationQueryService operationQueryService;
    private final AccrualMapper mapper;

    public AccrualController(AccrualService accrualService, OperationQueryService operationQueryService,
                             AccrualMapper mapper) {
        this.accrualService = accrualService;
        this.operationQueryService = operationQueryService;
        this.mapper = mapper;
    }

    @PostMapping("/preview")
    @Operation(summary = "Предварительный расчёт", description = "Сумма × коэффициент кампании по каждому сотруднику "
            + "и признак превышения лимита. Ничего не сохраняет; причина необязательна.")
    public AccrualPreviewDto preview(@AuthenticationPrincipal AuthenticatedUser me,
                                     @Valid @RequestBody AccrualPreviewRequestDto request) {
        return mapper.toDto(accrualService.preview(me.userId(), mapper.toCommand(request)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Начислить", description = "Итог ≤ лимита — «accrued», выше лимита — «pending» на согласование "
            + "финотдела. Руководитель — только своей команде (403). Заблокированный счёт — 423, начисление не проводится.")
    public AccrualResultDto create(@AuthenticationPrincipal AuthenticatedUser me,
                                   @Valid @RequestBody AccrualRequestDto request) {
        AccrualResult result = accrualService.create(me.userId(), mapper.toCommand(request));
        return mapper.toDto(result,
                operationQueryService.getAll(result.operations().stream().map(OperationView::id).toList()));
    }
}
