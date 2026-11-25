package by.technosoft.bonus.service.dto;

/**
 * Итог пакетной обработки событий (UC4).
 *
 * @param total    сколько событий взято в обработку
 * @param accrued  создано начислений
 * @param noBonus  обработано без начисления
 * @param skipped  пропущено как уже обработанные
 * @param failed   завершилось ошибкой
 */
public record BatchProcessingResult(int total, int accrued, int noBonus, int skipped, int failed) {
}
