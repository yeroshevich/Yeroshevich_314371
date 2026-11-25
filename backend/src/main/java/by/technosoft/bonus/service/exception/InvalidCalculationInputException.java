package by.technosoft.bonus.service.exception;

/** Некорректные входные данные расчёта: начисление не производится (альт. поток UC6). HTTP 422. */
public class InvalidCalculationInputException extends BonusDomainException {

    public static final String CODE = "INVALID_CALCULATION_INPUT";

    public InvalidCalculationInputException(String message) {
        super(CODE, message);
    }
}
