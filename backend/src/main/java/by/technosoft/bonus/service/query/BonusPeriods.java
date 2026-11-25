package by.technosoft.bonus.service.query;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.IsoFields;

import by.technosoft.bonus.domain.RulePeriodicity;

/**
 * Границы бизнес-периодов в часовом поясе компании (DRY: используется отчётами, аналитикой, правилами).
 * Период правила: месяц (и «по событию»), квартал, год.
 */
public final class BonusPeriods {

    private BonusPeriods() {
    }

    /** Начало суток {@code date} в зоне {@code zone}. */
    public static OffsetDateTime startOfDay(LocalDate date, ZoneId zone) {
        return date.atStartOfDay(zone).toOffsetDateTime();
    }

    /** Первый день периода правила, содержащего дату. */
    public static LocalDate periodStart(RulePeriodicity periodicity, LocalDate date) {
        return switch (periodicity) {
            case MONTH, EVENT -> date.withDayOfMonth(1);
            case QUARTER -> date.with(IsoFields.DAY_OF_QUARTER, 1);
            case YEAR -> date.withDayOfYear(1);
        };
    }

    /** Первый день следующего периода правила — с него вступают в силу отложенные изменения (UC8). */
    public static LocalDate nextPeriodStart(RulePeriodicity periodicity, LocalDate date) {
        LocalDate start = periodStart(periodicity, date);
        return switch (periodicity) {
            case MONTH, EVENT -> start.plusMonths(1);
            case QUARTER -> start.plusMonths(3);
            case YEAR -> start.plusYears(1);
        };
    }
}
