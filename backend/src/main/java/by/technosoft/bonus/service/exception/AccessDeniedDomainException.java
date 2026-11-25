package by.technosoft.bonus.service.exception;

/** Роль или область ответственности пользователя не позволяет выполнить действие (SEC-3). HTTP 403. */
public class AccessDeniedDomainException extends BonusDomainException {

    public static final String CODE = "ACCESS_DENIED";

    public AccessDeniedDomainException(String message) {
        super(CODE, message);
    }
}
