package by.technosoft.bonus.web.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** Предварительный расчёт начисления (TS: {@code AccrualPreview}). */
@Schema(name = "AccrualPreview")
public record AccrualPreviewDto(List<AccrualPreviewLineDto> lines, int total,
                                @Schema(example = "1000") int limit, int approvalCount) {
}
