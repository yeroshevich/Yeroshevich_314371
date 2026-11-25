package by.technosoft.bonus.web.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** Результат ручного начисления (TS: {@code AccrualResult}). */
@Schema(name = "AccrualResult")
public record AccrualResultDto(List<OperationDto> operations, int accruedCount, int pendingCount) {
}
