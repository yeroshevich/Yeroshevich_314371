package by.technosoft.bonus.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Входные данные расчёта бонуса по правилу (UC6).
 *
 * @param employeeId сотрудник
 * @param ruleId     правило начисления
 * @param kpiValue   уровень достижения KPI, % (для правил «по событию» может отсутствовать)
 * @param onDate     дата, на которую выбираются правило и кампании
 */
public record CalculationRequest(Long employeeId, Long ruleId, BigDecimal kpiValue, LocalDate onDate) {
}
