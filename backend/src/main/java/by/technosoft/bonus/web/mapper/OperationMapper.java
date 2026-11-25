package by.technosoft.bonus.web.mapper;

import static by.technosoft.bonus.web.mapper.ApiValues.code;
import static by.technosoft.bonus.web.mapper.ApiValues.id;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import by.technosoft.bonus.domain.Employee;
import by.technosoft.bonus.domain.Operation;
import by.technosoft.bonus.domain.OperationStatus;
import by.technosoft.bonus.domain.OperationType;
import by.technosoft.bonus.service.dto.CorrectionAction;
import by.technosoft.bonus.service.dto.CorrectionRequest;
import by.technosoft.bonus.service.query.OperationQueryService.OperationEntry;
import by.technosoft.bonus.service.query.OperationQueryService.OperationFilter;
import by.technosoft.bonus.web.dto.CorrectionRequestDto;
import by.technosoft.bonus.web.dto.OperationDto;

/**
 * Операция ↔ JSON. Единственное место, где собирается {@link OperationDto}: им пользуются
 * счёт сотрудника, журнал, контроль финотдела, начисления и списания (DRY).
 */
@Component
public class OperationMapper {

    /** Автор автоначисления (created_by = NULL). */
    static final String SYSTEM_AUTHOR = "Система";

    public OperationDto toDto(OperationEntry entry) {
        Operation op = entry.operation();
        Employee employee = op.getAccount().getEmployee();
        return new OperationDto(
                id(op.getId()),
                op.getNumber(),
                id(employee.getId()),
                employee.getFullName(),
                id(employee.getDepartment().getId()),
                code(op.getType()),
                op.getAmount(),
                op.getOriginalAmount(),
                code(op.getStatus()),
                code(op.getBasis()),
                op.getReason(),
                op.getRule() == null ? null : id(op.getRule().getId()),
                op.getCampaign() == null ? null : id(op.getCampaign().getId()),
                op.getProgram() == null ? null : id(op.getProgram().getId()),
                op.getCreatedAt(),
                op.getCreatedBy() == null ? SYSTEM_AUTHOR : op.getCreatedBy().getEmployee().getFullName(),
                entry.comment(),
                entry.disputed());
    }

    public List<OperationDto> toDtos(List<OperationEntry> entries) {
        return entries.stream().map(this::toDto).toList();
    }

    public CorrectionRequest toCommand(CorrectionRequestDto dto) {
        CorrectionAction action = "cancel".equals(dto.action()) ? CorrectionAction.CANCEL : CorrectionAction.ADJUST;
        return new CorrectionRequest(action, action == CorrectionAction.CANCEL ? null : dto.newAmount(), dto.reason());
    }

    /** Фильтр журнала из параметров запроса. */
    public OperationFilter toFilter(Long employeeId, Long departmentId, String type, List<String> statuses,
                                    LocalDate from, LocalDate to, String query) {
        Set<OperationStatus> statusSet = statuses == null || statuses.isEmpty() ? null
                : EnumSet.copyOf(statuses.stream().map(s -> ApiValues.parse(OperationStatus.class, s)).toList());
        return new OperationFilter(employeeId, departmentId, ApiValues.parse(OperationType.class, type), statusSet,
                from, to, query);
    }
}
