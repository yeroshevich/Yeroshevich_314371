package by.technosoft.bonus.web.mapper;

import java.time.YearMonth;
import java.util.Locale;

import org.springframework.stereotype.Component;

import by.technosoft.bonus.service.query.AnalyticsPeriod;
import by.technosoft.bonus.service.query.AnalyticsQueryService.Comparison;
import by.technosoft.bonus.service.query.AnalyticsQueryService.Dashboard;
import by.technosoft.bonus.web.dto.AnalyticsDashboardDto;
import by.technosoft.bonus.web.dto.AnalyticsDashboardDto.DepartmentComparison;
import by.technosoft.bonus.web.dto.AnalyticsDashboardDto.MonthlyFlow;

/** Дашборд эффективности (UC11) → JSON с подписями периодов на русском. */
@Component
public class AnalyticsMapper {

    private static final String[] MONTHS = {"январь", "февраль", "март", "апрель", "май", "июнь", "июль",
            "август", "сентябрь", "октябрь", "ноябрь", "декабрь"};
    private static final String[] SHORT_MONTHS = {"янв", "фев", "мар", "апр", "май", "июн", "июл", "авг", "сен",
            "окт", "ноя", "дек"};

    public AnalyticsDashboardDto toDto(Dashboard d) {
        int months = d.period().months();
        return new AnalyticsDashboardDto(
                code(d.period()),
                label(d.currentStart(), months),
                label(d.previousStart(), months),
                comparison(d.averageBonus()),
                comparison(d.programConversion()),
                comparison(d.totalAccrued()),
                comparison(d.totalSpent()),
                d.byDepartment().stream()
                        .map(x -> new DepartmentComparison(x.department(), x.current(), x.previous())).toList(),
                d.monthly().stream()
                        .map(m -> new MonthlyFlow(shortMonth(m.month()), m.accrued(), m.spent())).toList(),
                d.partial());
    }

    public static AnalyticsPeriod parse(String code) {
        return AnalyticsPeriod.valueOf(code.toUpperCase(Locale.ROOT));
    }

    static String code(AnalyticsPeriod period) {
        return period.name().toLowerCase(Locale.ROOT);
    }

    /** «ноябрь 2026» или «сен – ноя 2026». */
    static String label(YearMonth start, int months) {
        if (months == 1) {
            return MONTHS[start.getMonthValue() - 1] + " " + start.getYear();
        }
        YearMonth end = start.plusMonths(months - 1L);
        return shortMonth(start) + " – " + shortMonth(end) + " " + end.getYear();
    }

    private static String shortMonth(YearMonth month) {
        return SHORT_MONTHS[month.getMonthValue() - 1];
    }

    private static AnalyticsDashboardDto.Comparison comparison(Comparison c) {
        return new AnalyticsDashboardDto.Comparison(c.current(), c.previous());
    }
}
