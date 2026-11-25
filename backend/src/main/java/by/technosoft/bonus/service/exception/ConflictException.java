package by.technosoft.bonus.service.exception;

/**
 * Действие противоречит текущему состоянию данных (дубль, объект используется, не выполнено условие). HTTP 409.
 * Наследники уточняют код ошибки, HTTP-статус наследуется (обработчик ошибок REST-слоя не меняется).
 */
public class ConflictException extends BonusDomainException {

    public static final String CODE = "CONFLICT";

    public ConflictException(String message) {
        this(CODE, message);
    }

    public ConflictException(String code, String message) {
        super(code, message);
    }
}
