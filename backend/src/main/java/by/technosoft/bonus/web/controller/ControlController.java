package by.technosoft.bonus.web.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import by.technosoft.bonus.service.ReviewService;
import by.technosoft.bonus.service.query.OperationQueryService;
import by.technosoft.bonus.web.dto.BlockRequest;
import by.technosoft.bonus.web.dto.OperationDto;
import by.technosoft.bonus.web.dto.ReviewRequest;
import by.technosoft.bonus.web.mapper.OperationMapper;
import by.technosoft.bonus.web.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Контроль финансового отдела: согласование и блокировка операций. */
@RestController
@RequestMapping(ApiPaths.V1 + "/control/operations")
@PreAuthorize(Roles.FINANCE)
@Tag(name = "Контроль финотдела", description = "Начисления сверх лимита (UC5) и корректировки в минус (UC7)")
public class ControlController {

    private final OperationQueryService operationQueryService;
    private final ReviewService reviewService;
    private final OperationMapper mapper;

    public ControlController(OperationQueryService operationQueryService, ReviewService reviewService,
                             OperationMapper mapper) {
        this.operationQueryService = operationQueryService;
        this.reviewService = reviewService;
        this.mapper = mapper;
    }

    @GetMapping
    @Operation(summary = "Очередь контроля", description = "Операции на согласовании (старые сверху) и спорные.")
    public List<OperationDto> queue() {
        return mapper.toDtos(operationQueryService.controlQueue());
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Подтвердить", description = "Только операции pending. 409 — операция уже обработана.")
    public OperationDto approve(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id,
                                @Valid @RequestBody(required = false) ReviewRequest request) {
        reviewService.approve(id, me.userId(), request == null ? null : request.comment());
        return mapper.toDto(operationQueryService.get(id));
    }

    @PostMapping("/{id}/block")
    @Operation(summary = "Заблокировать", description = "Причина обязательна (422), её увидит сотрудник.")
    public OperationDto block(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id,
                              @Valid @RequestBody BlockRequest request) {
        reviewService.block(id, me.userId(), request.reason());
        return mapper.toDto(operationQueryService.get(id));
    }
}
