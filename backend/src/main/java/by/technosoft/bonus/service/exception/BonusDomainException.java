package by.technosoft.bonus.service.exception;

/**
 * Базовое доменное исключение: нарушение бизнес-правила или некорректный запрос.
 * {@link #getCode()} — машинный код ошибки, который REST-слой (ЛР9) вернёт клиенту
 * вместе с HTTP-статусом (см. {@code ApiError.code} на клиенте).
 */
public abstract class BonusDomainException extends RuntimeException {

    private final String code;

    protected BonusDomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
