package by.technosoft.bonus.web.mapper;

import static by.technosoft.bonus.web.mapper.ApiValues.id;

import java.util.List;

import org.springframework.stereotype.Component;

import by.technosoft.bonus.domain.OperationBasis;
import by.technosoft.bonus.service.dto.AccrualPreview;
import by.technosoft.bonus.service.dto.AccrualRequest;
import by.technosoft.bonus.service.dto.AccrualResult;
import by.technosoft.bonus.service.query.OperationQueryService.OperationEntry;
import by.technosoft.bonus.web.dto.AccrualPreviewDto;
import by.technosoft.bonus.web.dto.AccrualPreviewLineDto;
import by.technosoft.bonus.web.dto.AccrualPreviewRequestDto;
import by.technosoft.bonus.web.dto.AccrualRequestDto;
import by.technosoft.bonus.web.dto.AccrualResultDto;

/** Ручное начисление (UC5, UC6): JSON → команда сервиса, результат → JSON. */
@Component
public class AccrualMapper {

    private final OperationMapper operationMapper;

    public AccrualMapper(OperationMapper operationMapper) {
        this.operationMapper = operationMapper;
    }

    /** Причина-заглушка для расчёта без сохранения: сервис проверяет заполненность причины. */
    static final String PREVIEW_REASON = "Предварительный расчёт";

    /** Расчёт без сохранения: пустая причина допустима. */
    public AccrualRequest toCommand(AccrualPreviewRequestDto dto) {
        String reason = dto.reason() == null || dto.reason().isBlank() ? PREVIEW_REASON : dto.reason();
        return new AccrualRequest(dto.employeeIds(), dto.amount(), ApiValues.parse(OperationBasis.class, dto.basis()),
                reason, dto.ruleId(), dto.campaignId());
    }

    public AccrualRequest toCommand(AccrualRequestDto dto) {
        return new AccrualRequest(dto.employeeIds(), dto.amount(), ApiValues.parse(OperationBasis.class, dto.basis()),
                dto.reason(), dto.ruleId(), dto.campaignId());
    }

    public AccrualPreviewDto toDto(AccrualPreview preview) {
        List<AccrualPreviewLineDto> lines = preview.lines().stream()
                .map(l -> new AccrualPreviewLineDto(id(l.employeeId()), l.employeeName(), l.baseAmount(),
                        ApiValues.number(l.coefficient()), l.total(), l.needsApproval()))
                .toList();
        return new AccrualPreviewDto(lines, preview.total(), preview.limit(), preview.approvalCount());
    }

    /** @param operations созданные операции с пояснениями (в порядке результата) */
    public AccrualResultDto toDto(AccrualResult result, List<OperationEntry> operations) {
        return new AccrualResultDto(operationMapper.toDtos(operations), result.accruedCount(), result.pendingCount());
    }
}
