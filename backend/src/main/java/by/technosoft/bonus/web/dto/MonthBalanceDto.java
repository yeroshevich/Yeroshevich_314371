package by.technosoft.bonus.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Баланс на конец месяца. */
@Schema(name = "MonthBalance")
public record MonthBalanceDto(@Schema(example = "2026-11") String month, int balance) {
}
