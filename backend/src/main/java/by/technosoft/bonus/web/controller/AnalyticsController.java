package by.technosoft.bonus.web.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import by.technosoft.bonus.service.query.AnalyticsQueryService;
import by.technosoft.bonus.web.dto.AnalyticsDashboardDto;
import by.technosoft.bonus.web.mapper.AnalyticsMapper;
import by.technosoft.bonus.web.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;

/** Анализ эффективности бонусной системы (UC11). */
@RestController
@RequestMapping(ApiPaths.V1 + "/analytics")
@PreAuthorize(Roles.MANAGER_OR_HR)
@Tag(name = "Аналитика", description = "UC11 «Анализ эффективности»")
public class AnalyticsController {

    private final AnalyticsQueryService analyticsQueryService;
    private final AnalyticsMapper mapper;

    public AnalyticsController(AnalyticsQueryService analyticsQueryService, AnalyticsMapper mapper) {
        this.analyticsQueryService = analyticsQueryService;
        this.mapper = mapper;
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Дашборд", description = "Сравнение с предыдущим периодом той же длины; руководитель — своё "
            + "подразделение, HR — вся компания.")
    public AnalyticsDashboardDto dashboard(
            @AuthenticationPrincipal AuthenticatedUser me,
            @Parameter(description = "month | quarter | halfyear") @RequestParam(defaultValue = "month")
            @Pattern(regexp = "month|quarter|halfyear", message = "Период: month, quarter или halfyear.") String period) {
        return mapper.toDto(analyticsQueryService.dashboard(me.userId(), AnalyticsMapper.parse(period)));
    }
}
