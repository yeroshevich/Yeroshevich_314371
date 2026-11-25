package by.technosoft.bonus.web.dto;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

/** Итог сохранения правила (TS: {@code RuleSaveResult}). */
@Schema(name = "RuleSaveResult")
public record RuleSaveResultDto(
        RuleDto rule,
        @Schema(description = "Дата вступления изменений в силу") LocalDate effectiveFrom,
        @Schema(description = "Изменения отложены до следующего периода (альт. поток UC8)") boolean deferred) {
}
