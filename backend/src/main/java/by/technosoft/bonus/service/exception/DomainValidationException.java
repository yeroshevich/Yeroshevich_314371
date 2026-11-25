package by.technosoft.bonus.service.exception;

/** Запрос не прошёл бизнес-валидацию (пустая причина, недопустимая сумма и т. п.). HTTP 422. */
public class DomainValidationException extends BonusDomainException {

    public static final String CODE = "VALIDATION_FAILED";

    public DomainValidationException(String message) {
        super(CODE, message);
    }
}
