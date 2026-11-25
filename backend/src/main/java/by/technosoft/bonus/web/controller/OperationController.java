package by.technosoft.bonus.web.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import by.technosoft.bonus.service.CorrectionService;
import by.technosoft.bonus.service.dto.CorrectionResult;
import by.technosoft.bonus.service.query.OperationQueryService;
import by.technosoft.bonus.web.dto.CorrectionRequestDto;
import by.technosoft.bonus.web.dto.OperationDto;
import by.technosoft.bonus.web.mapper.OperationMapper;
import by.technosoft.bonus.web.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Журнал операций и корректировка (UC7). */
@RestController
@RequestMapping(ApiPaths.V1 + "/operations")
@Tag(name = "Журнал операций", description = "Поиск операций, UC7 «Корректировка начисления»")
public class OperationController {

    private final OperationQueryService operationQueryService;
    private final CorrectionService correctionService;
    private final OperationMapper mapper;

    public OperationController(OperationQueryService operationQueryService, CorrectionService correctionService,
                               OperationMapper mapper) {
        this.operationQueryService = operationQueryService;
        this.correctionService = correctionService;
        this.mapper = mapper;
    }

    @GetMapping
    @PreAuthorize(Roles.HR_OR_FINANCE)
    @Operation(summary = "Журнал операций с фильтрами", description = "Все параметры необязательны; status можно "
            + "повторять (status=pending&status=blocked) или перечислять через запятую.")
    public List<OperationDto> search(
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) Long departmentId,
            @Parameter(description = "accrual | writeoff | correction") @RequestParam(required = false)
            @Pattern(regexp = "accrual|writeoff|correction", message = "Тип: accrual, writeoff или correction.") String type,
            @Parameter(description = "accrued | written_off | corrected | pending | blocked | cancelled")
            @RequestParam(required = false)
            List<@Pattern(regexp = "accrued|written_off|corrected|pending|blocked|cancelled",
                    message = "Неизвестный статус операции.") String> status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Поиск по номеру, ФИО или причине") @RequestParam(required = false)
            @Size(max = 100, message = "Строка поиска — не длиннее 100 символов.") String q) {
        return mapper.toDtos(operationQueryService.search(
                mapper.toFilter(employeeId, departmentId, type, status, from, to, q)));
    }

    @PutMapping("/{id}")
    @PreAuthorize(Roles.MANAGER_OR_HR)
    @Operation(summary = "Корректировка или отмена", description = "Возвращает новую операцию типа correction. "
            + "Если баланс стал бы отрицательным — статус pending (согласование финотдела). "
            + "409 OPERATION_STATE_CONFLICT для списаний и уже изменённых операций.")
    public OperationDto correct(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id,
                                @Valid @RequestBody CorrectionRequestDto request) {
        CorrectionResult result = correctionService.correct(id, mapper.toCommand(request), me.userId());
        return mapper.toDto(operationQueryService.get(result.correction().id()));
    }
}
