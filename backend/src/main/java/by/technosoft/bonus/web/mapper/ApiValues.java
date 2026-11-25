package by.technosoft.bonus.web.mapper;

import java.math.BigDecimal;

import by.technosoft.bonus.domain.PersistentEnum;

/**
 * Общие правила представления значений в JSON (DRY): идентификаторы — строками,
 * перечисления — кодами БД в нижнем регистре, дробные числа — без хвостовых нулей.
 */
public final class ApiValues {

    private ApiValues() {
    }

    public static String id(Long id) {
        return id == null ? null : String.valueOf(id);
    }

    public static String code(PersistentEnum value) {
        return value == null ? null : value.dbValue();
    }

    /** Значение перечисления по коду из запроса (код уже проверен Bean Validation). */
    public static <E extends Enum<E> & PersistentEnum> E parse(Class<E> type, String code) {
        return code == null ? null : PersistentEnum.fromDb(type, code);
    }

    /** 0.40 → 0.4, 80.00 → 80. */
    public static BigDecimal number(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }
}
