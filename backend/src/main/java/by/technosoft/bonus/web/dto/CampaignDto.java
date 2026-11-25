package by.technosoft.bonus.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/** Бонусная кампания (UC9, TS: {@code Campaign}). */
@Schema(name = "Campaign")
public record CampaignDto(
        String id,
        String name,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        List<String> departmentIds,
        @Schema(example = "1.5") BigDecimal coefficient,
        @Schema(description = "Бюджет, баллы; null — бюджет не выделен", nullable = true)
        @JsonInclude(JsonInclude.Include.ALWAYS) Integer budget,
        @Schema(description = "Израсходовано (проведённые начисления по кампании)") int spent,
        @Schema(allowableValues = {"active", "planned", "needs_approval", "finished"}) String status) {
}
