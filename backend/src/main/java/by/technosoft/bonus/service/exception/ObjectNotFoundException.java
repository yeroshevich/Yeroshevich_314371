package by.technosoft.bonus.service.exception;

/** Запрошенный объект не найден. HTTP 404. */
public class ObjectNotFoundException extends BonusDomainException {

    public static final String CODE = "NOT_FOUND";

    public ObjectNotFoundException(String message) {
        super(CODE, message);
    }
}
